# Evacuation Management System — Backend Reference

## Project
AI-Based Congestion Prediction Framework for Dynamic Environments.
Spring Boot 3.2 / Java 17 backend. Frontend must integrate against these APIs.

## Base URL
```
http://localhost:8080
```

## Auth
All protected routes require:
```
Authorization: Bearer <jwt-token>
```
Token is obtained from `/api/auth/login` or `/api/auth/register`.
Token expiry: 24 hours.

Role-based access:
- `/api/admin/**` → ROLE_ADMIN only
- `/api/student/**` → ROLE_STUDENT only
- `/api/congestion/**` → any authenticated user
- `/api/auth/**` → public

---

## SYSTEM FLOWS

### Flow 1 — Admin Registration & Login
```
POST /api/auth/register
Body: { email, password, full_name, role: "ADMIN", adminCode: "EvacuationAdmin2026Secret" }
Response: { token, user: { id, email, full_name, role, building_id } }

POST /api/auth/login
Body: { email, password }
Response: { token, user: { id, email, full_name, role, building_id } }

GET /api/auth/me
Headers: Authorization: Bearer <token>
Response: { id, email, full_name, role, building_id, current_zone_id, current_floor_id, current_building_id }
```

### Flow 2 — Password Reset
```
POST /api/auth/forgot-password
Body: { email }
Response: { message: "If the email exists, a reset link was sent" }
→ Sends email with link: <frontend_url>/reset-password?token=<uuid>

POST /api/auth/reset-password
Body: { token, password }
Response: { message: "Password reset successfully" }
```

### Flow 3 — Building & Floor Setup (Admin)
```
GET  /api/admin/buildings
Response: [{ id, name, address, total_capacity, floors: [...], zone_count }]

POST /api/admin/buildings
Body: { name, address, total_capacity? }
Response: { id, name, address, total_capacity }

PUT  /api/admin/buildings/{id}
Body: { name?, address?, total_capacity? }

DELETE /api/admin/buildings/{id}

POST /api/admin/buildings/{buildingId}/floors
Body: { floor_number, label? }
Response: { id, building_id, floor_number, label }
```

### Flow 4 — Blueprint Upload & Manual Zone Creation (Admin)
```
STEP 1 — Upload blueprint image (display only, no AI detection)
POST /api/admin/floors/{floorId}/blueprint
Content-Type: multipart/form-data
Form field: file (image/png or image/jpeg)
Response: { blueprint_url: "uploads/blueprints/...", zones: [] }

STEP 2 — Get blueprint + existing zones for a floor
GET /api/admin/floors/{floorId}/blueprint
Response: { floor_id, blueprint_url, zones: [...] }

STEP 3 — Admin manually creates each zone via form
POST /api/admin/floors/{floorId}/zones
Headers: Authorization: Bearer <admin-token>
Body: {
  name: "Room 101",
  zone_type: "classroom",       // classroom | hallway | stairwell | lobby | exit | office | open_area
  max_capacity: 40,
  is_assembly_point: false,
  is_exit: false
}
Response: {
  id, name, zone_type, max_capacity, floor_id, building_id,
  status: "ACTIVE",
  qr_code_url: "uploads/qr/zone-{id}.png"
}
→ QR code is auto-generated immediately on zone creation.
→ Zone is set to ACTIVE immediately — no confirmation step needed.
→ Student who scans this QR will be checked in to this zone.

STEP 4 — Download QR to print and stick on the wall
GET /api/admin/zones/{id}/qr
Response: PNG image bytes (Content-Type: image/png)
→ Admin prints this and physically places it in the zone.

STEP 5 — Delete a zone if needed
DELETE /api/admin/zones/{id}
Response: { message: "Zone deleted" }

OTHER zone endpoints:
PUT  /api/admin/zones/{id}/coordinates  Body: { coordinates: [[x,y],...] }
POST /api/admin/zones/{id}/route        Body: { exit_name, coordinates: [[x,y],...] }
POST /api/admin/zones/{id}/regenerate-qr → Regenerates QR if lost
```

### Zone Form Fields (frontend)
| Field | Type | Required | Values |
|-------|------|----------|--------|
| name | text | yes | e.g. "Room 101", "Main Hallway" |
| zone_type | dropdown | yes | classroom, hallway, stairwell, lobby, exit, office, open_area |
| max_capacity | number | yes | e.g. 40 |
| is_assembly_point | checkbox | no | true if this is where students gather after evacuation |
| is_exit | checkbox | no | true if this is a building exit |

### Zone Page Layout (frontend)
```
┌─────────────────────────┬───────────────────────────────────┐
│   Blueprint (left)       │   Zone Management (right)         │
│                          │                                   │
│  [floor plan image]      │  ┌─ Add New Zone ──────────────┐  │
│                          │  │ Name:     [______________]  │  │
│                          │  │ Type:     [dropdown      ▼] │  │
│                          │  │ Capacity: [______________]  │  │
│                          │  │ [ ] Assembly Point          │  │
│                          │  │ [ ] Exit Zone               │  │
│                          │  │         [Add Zone]          │  │
│                          │  └────────────────────────────┘  │
│                          │                                   │
│                          │  Zones (2)                        │
│                          │  ┌────────────────────────────┐  │
│                          │  │ 🟢 Room 101 • classroom     │  │
│                          │  │    Capacity: 40             │  │
│                          │  │ [Download QR] [Delete]      │  │
│                          │  ├────────────────────────────┤  │
│                          │  │ 🟢 Main Hallway • hallway   │  │
│                          │  │    Capacity: 20             │  │
│                          │  │ [Download QR] [Delete]      │  │
│                          │  └────────────────────────────┘  │
└─────────────────────────┴───────────────────────────────────┘
```

### QR Code Behavior
- QR payload contains: { zoneId, floorId, buildingId, sig }
- sig = HMAC-SHA256 signature (validated server-side on check-in)
- Student scans QR → frontend reads payload → POST /api/student/checkin with all 4 fields
- Student is immediately checked in to that zone

### Flow 5 — Student QR Check-In
```
POST /api/student/checkin
Headers: Authorization: Bearer <student-token>
Body: { zoneId, floorId, buildingId, sig }
→ sig is HMAC-SHA256 signature embedded in QR code. Frontend reads it from QR.
→ Validates signature, checks zone is ACTIVE, logs check-in, sets student location.
→ If zone is assembly point and evacuation active → auto-marks student as safe.
Response: { message, zone, floor_id, building_id }

GET /api/student/blueprint/{floorId}
→ Returns floor zones for rendering the map
Response: { floor_id, zones: [...] }
```

### Flow 6 — Manual Location Update
```
PUT /api/student/location
Headers: Authorization: Bearer <student-token>
Body: { zoneId, floorId, buildingId }
→ No QR required. Student manually updates their location.
Response: { message }
```

### Flow 7 — Congestion Prediction (AI Model)
```
POST /api/congestion/predict
Headers: Authorization: Bearer <token>
Body: {
  peopleCount: 80,
  maxCapacity: 100,
  zoneType: "hallway",        // hallway | stairwell | classroom | lobby | exit | office | open_area
  movementSpeed: "slow",      // slow | normal | fast
  timeOfDay: "08:00",
  emergencyFlag: 1            // 0 or 1
}
Response: {
  congestion_level: "High",   // Low | Moderate | High | Critical
  recommendation: "Redirect people to alternate zones immediately.",
  zone_type: "hallway",
  people_count: 80,
  max_capacity: 100,
  density_ratio: 0.8
}
→ Calls Python RandomForest model via subprocess.
→ Python model must be trained first: cd python-model && python train_model.py
```

### Flow 8 — Evacuation Trigger (Admin)
```
POST /api/admin/evacuate
Headers: Authorization: Bearer <admin-token>
Body: {
  type: "EARTHQUAKE",         // any string: EARTHQUAKE | FIRE | FLOOD etc.
  message: "Evacuate now.",
  scope: {}                   // {} = ALL students
         OR { "zoneId": "..." }
         OR { "floorId": "..." }
         OR { "buildingId": "..." }
}
Response: { evacuationId, studentsAlerted, status: "ACTIVE" }
→ Broadcasts via WebSocket: /topic/evacuate/{buildingId}
→ Sends email to every affected student (async)
→ WebSocket payload: { evacuationId, type, message, scopeType, scopeId }

GET /api/admin/evacuate/{id}/status
Response: {
  evacuationId, status, type, triggeredAt,
  totalAlerted, acknowledged, safe, nonResponsive,
  acks: [{ studentId, acknowledgedAt, safeAt, safeMethod }]
}

PUT /api/admin/evacuate/{id}/end
→ Marks evacuation COMPLETED, sends all-clear email to all students
Response: { status: "COMPLETED", endedAt }

GET /api/admin/evacuations/history
Response: [{ id, type, scopeType, scopeId, status, triggeredAt, endedAt }]

GET /api/admin/evacuate/{id}/alerts
Response: [{ studentId, evacuationId, channel, deliveryStatus, sentAt }]
```

### Flow 9 — Student Evacuation Response
```
POST /api/student/evacuate/{evacuationId}/acknowledge
Headers: Authorization: Bearer <student-token>
→ Student taps "I received this alert"
→ Broadcasts to /topic/evacuation-status/{evacuationId}: { studentId, action: "ACKNOWLEDGED" }

POST /api/student/evacuate/{evacuationId}/safe
Headers: Authorization: Bearer <student-token>
→ Student taps "I am safe"
→ Broadcasts to /topic/evacuation-status/{evacuationId}: { studentId, action: "SAFE" }
→ safeMethod stored as "BUTTON"
→ If student checks in to assembly point zone via QR → safeMethod stored as "QR"

GET /api/student/evacuation-route
Headers: Authorization: Bearer <student-token>
→ Returns assigned exit route for student's current zone
Response: { zone_id, exit_name, coordinates }
```

### Flow 10 — Admin User Management
```
GET  /api/admin/users?page=0&size=20&role=STUDENT&search=john
POST /api/admin/users
     Body: { email, password, full_name, id_number?, mobile_number?, building_id?, role }

GET    /api/admin/users/{id}
PUT    /api/admin/users/{id}
       Body: { full_name?, mobile_number?, building_id?, role? }
PUT    /api/admin/users/{id}/status
       Body: { is_active: true|false }
DELETE /api/admin/users/{id}
       → Also deletes check-ins, acks, notifications for this user

POST /api/admin/users/{id}/reset-password
     Body: { new_password }

GET /api/admin/users/{id}/checkins
GET /api/admin/users/{id}/evacuation-history

GET /api/admin/students/locations
→ All students with current zone/floor/building. Used for live map.
Response: [{ id, full_name, email, current_zone_id, current_floor_id, current_building_id, zone_name }]
```

---

## STUDENT PROFILE & HISTORY
```
GET /api/student/checkins
GET /api/student/evacuations/history
GET /api/student/notifications

PUT /api/student/profile
    Body: { full_name?, mobile_number? }

PUT /api/student/change-password
    Body: { currentPassword, newPassword }
```

---

## ADMIN NOTIFICATIONS
```
GET /api/admin/notifications
→ All notification logs across all evacuations
```

---

## WEBSOCKET
Connect to: `ws://localhost:8080/ws` (SockJS endpoint)
Use STOMP over SockJS.

Subscribe topics:
```
/topic/evacuate/{buildingId}
→ Receives evacuation alerts and ALL_CLEAR events
Payload: { evacuationId, type, message, scopeType, scopeId }
ALL_CLEAR payload: { type: "ALL_CLEAR", evacuationId, buildingName }

/topic/evacuation-status/{evacuationId}
→ Real-time ack/safe updates
Payload: { studentId, action: "ACKNOWLEDGED" | "SAFE" | "SAFE_QR" }

/topic/occupancy/{buildingId}
→ Real-time check-in events for live map
Payload: { studentId, zoneId, action: "CHECKIN" }
```

---

## EMAIL EVENTS
Emails sent automatically (async, via Brevo SMTP):
| Trigger | Recipient | Template |
|---------|-----------|----------|
| Evacuation triggered | Each affected student | URGENT alert with zone + exit route |
| Evacuation ended | Each alerted student | ALL CLEAR |
| Forgot password | Requesting user | Reset link (expires 1 hour) |

From address: configured via `MAIL_FROM` env var (must be a verified Brevo sender).

---

## STUDENT REGISTRATION FIELDS
```json
{
  "email": "student@example.com",
  "password": "...",
  "full_name": "Juan dela Cruz",
  "id_number": "2021-12345",
  "mobile_number": "+639171234567",
  "building_id": "<uuid>",
  "role": "STUDENT"
}
```
`adminCode` is only required when `role` is `"ADMIN"`.

---

## ZONE STATUS LIFECYCLE
```
PENDING → (admin confirms) → ACTIVE
```
Only ACTIVE zones accept student check-ins.

## ZONE TYPES (for congestion prediction)
`hallway` | `stairwell` | `classroom` | `lobby` | `exit` | `office` | `open_area`

## CONGESTION LEVELS
| Level | Meaning | Recommendation |
|-------|---------|----------------|
| Low | < 40% density | Normal movement |
| Moderate | 40–65% density | Monitor, consider redistribution |
| High | 65–90% density | Redirect immediately |
| Critical | > 90% density | Clear the zone now |

---

## RESPONSE FORMAT NOTES
- All responses use `snake_case` keys (Jackson SNAKE_CASE strategy applied globally)
- Timestamps are ISO-8601 UTC strings
- UUIDs are used for all entity IDs
- Errors always return: `{ "error": "message" }`

---

## ENVIRONMENT VARIABLES (required to run)
```
MYSQL_URL        jdbc:mysql://localhost:3306/evacuation_db?...
MYSQL_USER       root
MYSQL_PASSWORD   root
JWT_SECRET       (min 32 chars)
ADMIN_CODE       (secret code for admin registration)
ANTHROPIC_API_KEY (for blueprint zone detection)
BREVO_USER       (SMTP login from Brevo)
BREVO_SMTP_KEY   (SMTP key from Brevo)
MAIL_FROM        (verified sender email in Brevo)
HMAC_SECRET      (for QR signature validation)
```

## RUNNING LOCALLY
```powershell
# 1. Train Python model (one time only)
cd python-model
pip install scikit-learn numpy
python train_model.py

# 2. Start backend
.\run-local.ps1
```
