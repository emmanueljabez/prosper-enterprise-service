# Mentor Availability API - Quick Reference

## Endpoints Overview

| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|---------------|
| `POST` | `/api/v1/mentor-availability` | Create single slot | ✅ MENTOR/ADMIN |
| `POST` | `/api/v1/mentor-availability/bulk` | Bulk create slots | ✅ MENTOR/ADMIN |
| `GET` | `/api/v1/mentor-availability/{id}` | Get slot by ID | ❌ |
| `GET` | `/api/v1/mentor-availability/mentor/{mentorId}` | Get all slots for mentor | ❌ |
| `GET` | `/api/v1/mentor-availability/mentor/{mentorId}/weekly` | Get weekly schedule | ❌ |
| `GET` | `/api/v1/mentor-availability/mentor/{mentorId}/day/{day}` | Get slots for specific day | ❌ |
| `GET` | `/api/v1/mentor-availability/me` | Get my availability | ✅ MENTOR |
| `GET` | `/api/v1/mentor-availability/me/weekly` | Get my weekly schedule | ✅ MENTOR |
| `PUT` | `/api/v1/mentor-availability/{id}` | Update slot | ✅ MENTOR/ADMIN |
| `PATCH` | `/api/v1/mentor-availability/{id}/toggle` | Toggle active status | ✅ MENTOR/ADMIN |
| `DELETE` | `/api/v1/mentor-availability/{id}` | Delete single slot | ✅ MENTOR/ADMIN |
| `DELETE` | `/api/v1/mentor-availability/mentor/{mentorId}/all` | Delete all slots | ✅ MENTOR/ADMIN |
| `DELETE` | `/api/v1/mentor-availability/mentor/{mentorId}/day/{day}` | Delete day slots | ✅ MENTOR/ADMIN |

## Quick Examples

### Create Single Slot
```bash
POST /api/v1/mentor-availability
{
  "mentorId": "uuid-here",
  "dayOfWeek": "MONDAY",
  "startTime": "09:00:00",
  "endTime": "12:00:00",
  "isActive": true
}
```

### Bulk Create
```bash
POST /api/v1/mentor-availability/bulk
{
  "mentorId": "uuid-here",
  "slots": [
    {"dayOfWeek": "MONDAY", "startTime": "09:00:00", "endTime": "12:00:00"},
    {"dayOfWeek": "WEDNESDAY", "startTime": "14:00:00", "endTime": "17:00:00"}
  ]
}
```

### Get Weekly Schedule
```bash
GET /api/v1/mentor-availability/mentor/{mentorId}/weekly?activeOnly=true
```

### Update Slot
```bash
PUT /api/v1/mentor-availability/{id}
{
  "startTime": "10:00:00",
  "endTime": "13:00:00"
}
```

### Toggle Status
```bash
PATCH /api/v1/mentor-availability/{id}/toggle
```

### Delete Slot
```bash
DELETE /api/v1/mentor-availability/{id}
```

## Valid Days of Week
- `MONDAY`
- `TUESDAY`
- `WEDNESDAY`
- `THURSDAY`
- `FRIDAY`
- `SATURDAY`
- `SUNDAY`

## Time Format
All times use 24-hour format: `HH:mm:ss`

Examples:
- `09:00:00` (9 AM)
- `14:30:00` (2:30 PM)
- `17:45:00` (5:45 PM)

## Response Format
All responses follow this structure:
```json
{
  "status": "success|error",
  "message": "Description",
  "data": { },
  "timestamp": "2025-10-11T14:30:00"
}
```

## Common Status Codes
- `200` - Success
- `201` - Created
- `400` - Bad Request (validation error)
- `401` - Unauthorized
- `403` - Forbidden
- `404` - Not Found
- `500` - Internal Server Error

## Features
✅ Days & timeslots (not dates)
✅ Overlap prevention
✅ Active/inactive toggle
✅ Bulk operations
✅ Weekly schedule view
✅ Role-based access control
✅ Validation (time ranges)

## Database Schema
```sql
mentor_availability
├── id (BIGSERIAL)
├── mentor_id (UUID)
├── day_of_week (VARCHAR)
├── start_time (TIME)
├── end_time (TIME)
├── is_active (BOOLEAN)
├── created_at (TIMESTAMP)
└── updated_at (TIMESTAMP)
```
