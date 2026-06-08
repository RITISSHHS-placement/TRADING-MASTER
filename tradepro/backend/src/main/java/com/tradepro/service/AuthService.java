package com.tradepro.service;

import com.tradepro.dto.*;
import com.tradepro.entity.User;
import com.tradepro.entity.BoundDevice;
import com.tradepro.entity.UserSession;
import com.tradepro.repository.UserRepository;
import com.tradepro.repository.BoundDeviceRepository;
import com.tradepro.repository.UserSessionRepository;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import com.warrenstrange.googleauth.GoogleAuthenticatorQRGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Transactional
public class AuthService {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private BoundDeviceRepository boundDeviceRepository;
    
    @Autowired
    private UserSessionRepository userSessionRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Autowired
    private JwtService jwtService;
    
    @Value("${totp.issuer:TradePro}")
    private String totpIssuer;
    
    private final GoogleAuthenticator gAuth = new GoogleAuthenticator();
    
    public AuthResponse register(RegisterRequest request) {
        // Check if user already exists
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("User with this email already exists");
        }
        
        if (userRepository.findByPhone(request.getPhone()).isPresent()) {
            throw new RuntimeException("User with this phone number already exists");
        }
        
        // Create new user
        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setKycStatus(User.KycStatus.PENDING);
        user.setRiskProfile(User.RiskProfile.MODERATE);
        user.setTradingEnabled(true);
        user.setSmsOtpEnabled(true);
        user.setTotpEnabled(false);
        user.setNudgesEnabled(true);
        user.setPerTradeLimit(100000.0); // Default limit
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        
        user = userRepository.save(user);
        
        // Generate JWT tokens
        String accessToken = jwtService.generateToken(user.getEmail());
        String refreshToken = jwtService.generateRefreshToken(user.getEmail());
        
        // Create user session
        UserSession session = new UserSession();
        session.setUser(user);
        session.setDeviceId(request.getDeviceId());
        session.setDeviceName(request.getDeviceName());
        session.setIpAddress(request.getIpAddress());
        session.setUserAgent(request.getUserAgent());
        session.setAccessToken(accessToken);
        session.setRefreshToken(refreshToken);
        session.setCreatedAt(LocalDateTime.now());
        session.setExpiresAt(LocalDateTime.now().plusDays(7));
        session.setActive(true);
        
        userSessionRepository.save(session);
        
        // Create UserDto for response
        UserDto userDto = new UserDto();
        userDto.setId(user.getId());
        userDto.setName(user.getName());
        userDto.setEmail(user.getEmail());
        userDto.setPhone(user.getPhone());
        userDto.setKycStatus(user.getKycStatus().toString());
        userDto.setRiskProfile(user.getRiskProfile().toString());
        userDto.setTradingEnabled(user.getTradingEnabled());
        userDto.setTotpEnabled(user.getTotpEnabled());
        
        return new AuthResponse(accessToken, refreshToken, userDto);
    }
    
    public AuthResponse login(LoginRequest request) {
        // Find user by email
        User user = userRepository.findByEmail(request.getEmail())
            .orElseThrow(() -> new RuntimeException("Invalid email or password"));
        
        // Check password
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid email or password");
        }
        
        // Check if trading is enabled (kill switch)
        if (!user.getTradingEnabled()) {
            throw new RuntimeException("Trading is disabled. Please contact support.");
        }
        
        // Check device binding if enabled
        if (request.getDeviceId() != null) {
            Optional<BoundDevice> boundDevice = boundDeviceRepository
                .findByUserAndDeviceId(user, request.getDeviceId());
            
            if (boundDevice.isEmpty()) {
                // New device - create bound device entry
                BoundDevice newDevice = new BoundDevice();
                newDevice.setUser(user);
                newDevice.setDeviceId(request.getDeviceId());
                newDevice.setDeviceName(request.getDeviceName());
                newDevice.setDeviceType("WEB");
                newDevice.setTrusted(false); // Requires verification
                newDevice.setCreatedAt(LocalDateTime.now());
                
                boundDeviceRepository.save(newDevice);
                
                throw new RuntimeException("New device detected. Please verify via email/SMS.");
            }
            
            if (!boundDevice.get().getTrusted()) {
                throw new RuntimeException("Device not trusted. Please verify via email/SMS.");
            }
        }
        
        // Generate JWT tokens
        String accessToken = jwtService.generateToken(user.getEmail());
        String refreshToken = jwtService.generateRefreshToken(user.getEmail());
        
        // Create/update user session
        UserSession session = new UserSession();
        session.setUser(user);
        session.setDeviceId(request.getDeviceId());
        session.setDeviceName(request.getDeviceName());
        session.setIpAddress(request.getIpAddress());
        session.setUserAgent(request.getUserAgent());
        session.setAccessToken(accessToken);
        session.setRefreshToken(refreshToken);
        session.setCreatedAt(LocalDateTime.now());
        session.setExpiresAt(LocalDateTime.now().plusDays(7));
        session.setActive(true);
        
        userSessionRepository.save(session);
        
        // Create UserDto for response
        UserDto userDto = new UserDto();
        userDto.setId(user.getId());
        userDto.setName(user.getName());
        userDto.setEmail(user.getEmail());
        userDto.setPhone(user.getPhone());
        userDto.setKycStatus(user.getKycStatus().toString());
        userDto.setRiskProfile(user.getRiskProfile().toString());
        userDto.setTradingEnabled(user.getTradingEnabled());
        userDto.setTotpEnabled(user.getTotpEnabled());
        
        return new AuthResponse(accessToken, refreshToken, userDto);
    }
    
    public AuthResponse refreshToken(String refreshToken) {
        String email = jwtService.extractUsername(refreshToken);
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        if (!jwtService.isTokenValid(refreshToken, email)) {
            throw new RuntimeException("Invalid refresh token");
        }
        
        String newAccessToken = jwtService.generateToken(email);
        String newRefreshToken = jwtService.generateRefreshToken(email);
        
        // Update session
        UserSession session = userSessionRepository.findByRefreshToken(refreshToken)
            .orElseThrow(() -> new RuntimeException("Session not found"));
        
        session.setAccessToken(newAccessToken);
        session.setRefreshToken(newRefreshToken);
        session.setExpiresAt(LocalDateTime.now().plusDays(7));
        
        userSessionRepository.save(session);
        
        // Create UserDto for response
        UserDto userDto = new UserDto();
        userDto.setId(user.getId());
        userDto.setName(user.getName());
        userDto.setEmail(user.getEmail());
        userDto.setPhone(user.getPhone());
        userDto.setKycStatus(user.getKycStatus().toString());
        userDto.setRiskProfile(user.getRiskProfile().toString());
        userDto.setTradingEnabled(user.getTradingEnabled());
        userDto.setTotpEnabled(user.getTotpEnabled());
        
        return new AuthResponse(newAccessToken, newRefreshToken, userDto);
    }
    
    public TotpSetupResponse setupTotp(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        GoogleAuthenticatorKey key = gAuth.createCredentials();
        String secret = key.getKey();
        
        // Save secret to user
        user.setTotpSecret(secret);
        userRepository.save(user);
        
        // Generate QR code URL
        String qrCodeUrl = GoogleAuthenticatorQRGenerator.getOtpAuthTotpURL(
            totpIssuer, user.getEmail(), key);
        
        return new TotpSetupResponse(secret, qrCodeUrl, secret);
    }
    
    public boolean verifyTotp(Long userId, String code) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        if (user.getTotpSecret() == null) {
            throw new RuntimeException("TOTP not set up for this user");
        }
        
        boolean isValid = gAuth.authorize(user.getTotpSecret(), Integer.parseInt(code));
        
        if (isValid && !user.getTotpEnabled()) {
            // Enable TOTP for user
            user.setTotpEnabled(true);
            userRepository.save(user);
        }
        
        return isValid;
    }
    
    public void logout(String token) {
        // Extract token from Bearer format
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        
        // Find and deactivate session
        Optional<UserSession> session = userSessionRepository.findByAccessToken(token);
        if (session.isPresent()) {
            session.get().setActive(false);
            userSessionRepository.save(session.get());
        }
    }
}