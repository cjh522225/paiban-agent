# Smart Scheduling System

A smart scheduling management system built with Vue 3 + Spring Boot, supporting both **dormitory duty** and **office duty** scheduling, with admin and user portals.

## Tech Stack

| Layer | Technology |
|---|---|
| Frontend Framework | Vue 3 + TypeScript |
| Build Tool | Vite |
| UI Component Library | Element Plus |
| CSS Framework | Tailwind CSS |
| State Management | Pinia |
| Router | Vue Router |
| HTTP Client | Axios |
| Backend Framework | Spring Boot 3.2.5 |
| ORM | MyBatis-Plus 3.5.6 |
| Database | MySQL |
| Auth | JWT (jjwt 0.12.5) |
| Import/Export | Apache POI 5.2.5 |

## Features

### Admin Portal

| Module | Description |
|---|---|
| Dormitory Duty | View/generate dormitory schedules, manually adjust duty staff |
| Dormitory Management | CRUD for dormitory buildings |
| Office Duty | View/generate office schedules, manually adjust duty staff |
| Office Management | CRUD for offices |
| Availability | View all users' availability summary |
| Leave Management | Approve/reject leave requests |
| Messages | Send/manage system notifications |
| User Management | CRUD for users |
| Statistics | View duty statistics for dormitory/office |
| Semester Settings | Configure semester timeline and holidays |

### User Portal

| Module | Description |
|---|---|
| Dormitory Schedule | View personal dormitory duty schedule |
| Office Schedule | View personal office duty schedule |
| Availability | Set personal availability (odd/even/exam weeks) |
| Leave Request | Submit/cancel leave requests |

## Project Structure

```
paiban/
├── front/                  # Vue 3 frontend
│   └── src/
│       ├── api/            # API modules
│       ├── assets/         # Static assets
│       ├── components/     # Shared components
│       ├── layouts/        # Layout components
│       ├── router/         # Route config
│       ├── stores/         # Pinia stores
│       ├── utils/          # Utilities (Axios wrapper, etc.)
│       └── views/          # Page views
│           ├── admin/      # Admin pages
│           └── user/       # User pages
├── server/                 # Spring Boot backend
│   └── src/main/java/com/Firefire/paiban/
│       ├── common/         # Common classes (Result, etc.)
│       ├── config/         # Config (MyBatis-Plus, CORS, Interceptors)
│       ├── controller/     # Controllers
│       ├── dto/            # Data Transfer Objects
│       ├── entity/         # Entities
│       ├── interceptor/    # Interceptors
│       ├── mapper/         # MyBatis-Plus Mappers
│       ├── scheduling/     # Scheduling engine
│       ├── service/        # Services
│       └── util/           # Utilities
└── sql/                    # Database scripts
```

## Quick Start

### Prerequisites

- Node.js >= 18
- Java >= 21
- MySQL >= 8.0

### 1. Initialize Database

```bash
mysql -u root -p < server/src/main/resources/sql/init_schema.sql
# Optional: load sample data
mysql -u root -p < server/src/main/resources/sql/init_data.sql
```

Default admin credentials: `admin` / `123456`

### 2. Start Backend

```bash
cd server
./mvnw spring-boot:run
```

Backend runs on `http://localhost:8080`

### 3. Start Frontend

```bash
cd front
npm install
npm run dev
```

Frontend runs on `http://localhost:5173`

## Build & Deploy

```bash
# Frontend
cd front && npm run build    # Output: dist/

# Backend
cd server && ./mvnw package  # Output: target/
```
