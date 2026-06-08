# TradePro — Full-Stack Trading Platform

> Institutional-grade trading platform · Spring Boot + React · Auto-deploys via GitHub



## 🗂 Project Structure

```
tradepro/
├── .github/workflows/deploy.yml  ← CI/CD: auto-deploy on every push
├── backend/                      ← Spring Boot (Java 17)
│   ├── src/main/java/com/tradepro/
│   │   ├── config/               SecurityConfig, CORS
│   │   ├── controller/           Auth, Trade, User REST endpoints
│   │   ├── dto/                  Request/Response objects
│   │   ├── entity/               User, Trade, BoundDevice, UserSession
│   │   ├── exception/            GlobalExceptionHandler
│   │   ├── repository/           Spring Data JPA repos
│   │   ├── security/             JwtAuthenticationFilter
│   │   └── service/              AuthService, JwtService, TradeService
│   ├── src/main/resources/
│   │   ├── application.properties        (dev — H2 in-memory)
│   │   └── application-prod.properties   (prod — PostgreSQL via env vars)
│   ├── Dockerfile
│   ├── railway.toml
│   └── pom.xml
│
├── frontend/                     ← React 18 + Vite
│   ├── src/
│   │   ├── assets/styles/        global.css (design tokens)
│   │   ├── components/
│   │   │   ├── layout/           DashboardLayout, Sidebar
│   │   │   └── ui/               Button, Input, Card, Badge, Toggle
│   │   ├── hooks/                useAuth, useTrades, useKillSwitch
│   │   ├── pages/                Landing, Login, Register, Dashboard,
│   │   │                         Trade, Portfolio, Security, Settings
│   │   ├── services/api.js       Axios client + token refresh interceptor
│   │   ├── store/slices/         authSlice, tradeSlice, uiSlice
│   │   └── App.jsx               React Router with private routes
│   ├── public/
│   │   ├── 404.html              GitHub Pages SPA routing fix
│   │   └── favicon.svg
│   ├── Dockerfile
│   ├── nginx.conf
│   └── vite.config.js            base path support for GitHub Pages
│
├── docker-compose.yml            Local full-stack (Postgres + Backend + Frontend)
├── render.yaml                   One-click Render.com deploy
├── setup-github.sh               ← Run this first!
└── .gitignore
```

---




## 🔑 API Reference

### Public Endpoints (no token)
```
POST /api/auth/register      Register new user
POST /api/auth/login         Login → returns JWT
POST /api/auth/refresh       Refresh access token
GET  /api/health             Health check
```

### Protected Endpoints (Bearer token required)
```
POST   /api/auth/logout              Invalidate session
POST   /api/auth/setup-totp          Generate TOTP QR
POST   /api/auth/verify-totp         Confirm TOTP code

GET    /api/users/profile            Get current user
POST   /api/users/{id}/kill-switch   Halt all trading
PUT    /api/users/{id}/risk-profile  Update risk level
PUT    /api/users/{id}/auto-logout   Set inactivity timer
PUT    /api/users/{id}/nudges        Toggle AI nudges

POST   /api/trades/place/{userId}    Place order
GET    /api/trades/user/{userId}     Trade history
GET    /api/trades/positions/{id}    Open positions
DELETE /api/trades/{id}/cancel/{uid} Cancel order
GET    /api/trades/pnl/{userId}      Total P&L
```

---

## 🔒 Security Stack

| Feature | Implementation |
|---------|---------------|
| Password hashing | BCrypt (adaptive cost) |
| Access tokens | JWT HS256, 24h expiry |
| Refresh tokens | JWT, 7-day rotation |
| 2FA | TOTP (RFC 6238) via Google Auth |
| Device binding | New devices require email/SMS |
| Kill switch | Halts trading + revokes all sessions |
| Auto-logout | Configurable 15m–2h inactivity |
| CORS | Whitelist via env var |
| Session mgmt | UserSession entity, active/inactive |

---

## 🛠 Tech Stack

| | |
|--|--|
| **Backend** | Spring Boot 3.2, Spring Security, JPA |
| **Auth** | jjwt 0.12, BCrypt, GoogleAuth TOTP |
| **Database** | H2 (dev) / PostgreSQL (prod) |
| **Frontend** | React 18, Vite, Redux Toolkit |
| **Charts** | Recharts |
| **Styles** | CSS Modules + design tokens |
| **Deploy** | GitHub Pages (frontend) + Railway (backend) |
| **CI/CD** | GitHub Actions |

---


**Q: How do I update the app?**  
A: Just `git push`. GitHub Actions handles the rest automatically.

**Q: Can I use a custom domain?**  
A: Yes — in your repo Settings → Pages → Custom domain. But the default `github.io` URL always works too.
