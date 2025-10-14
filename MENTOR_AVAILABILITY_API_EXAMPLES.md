# Mentor Availability API Examples

This document provides comprehensive examples for using the Mentor Availability API.

## Base URL
```
http://localhost:8080/api/v1/mentor-availability
```

## Authentication
Most endpoints require authentication with JWT Bearer token:
```
Authorization: Bearer <your-jwt-token>
```

Required Roles:
- **MENTOR** - For creating/managing own availability
- **ADMIN** - For managing any mentor's availability

---

## 1. Create a Single Availability Slot

### Request
```bash
POST /api/v1/mentor-availability
Content-Type: application/json
Authorization: Bearer <token>
```

### Request Body
```json
{
  "mentorId": "123e4567-e89b-12d3-a456-426614174000",
  "dayOfWeek": "MONDAY",
  "startTime": "09:00:00",
  "endTime": "12:00:00",
  "isActive": true
}
```

### Response (201 Created)
```json
{
  "status": "success",
  "message": "Availability slot created successfully",
  "data": {
    "id": 1,
    "mentorId": "123e4567-e89b-12d3-a456-426614174000",
    "dayOfWeek": "MONDAY",
    "startTime": "09:00:00",
    "endTime": "12:00:00",
    "isActive": true,
    "durationInMinutes": 180,
    "createdAt": "2025-10-11T14:30:00",
    "updatedAt": "2025-10-11T14:30:00"
  },
  "timestamp": "2025-10-11T14:30:00"
}
```

### cURL Example
```bash
curl -X POST http://localhost:8080/api/v1/mentor-availability \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "mentorId": "123e4567-e89b-12d3-a456-426614174000",
    "dayOfWeek": "MONDAY",
    "startTime": "09:00:00",
    "endTime": "12:00:00",
    "isActive": true
  }'
```

---

## 2. Bulk Create Multiple Availability Slots

### Request
```bash
POST /api/v1/mentor-availability/bulk
Content-Type: application/json
Authorization: Bearer <token>
```

### Request Body
```json
{
  "mentorId": "123e4567-e89b-12d3-a456-426614174000",
  "slots": [
    {
      "dayOfWeek": "MONDAY",
      "startTime": "09:00:00",
      "endTime": "12:00:00",
      "isActive": true
    },
    {
      "dayOfWeek": "MONDAY",
      "startTime": "14:00:00",
      "endTime": "17:00:00",
      "isActive": true
    },
    {
      "dayOfWeek": "WEDNESDAY",
      "startTime": "10:00:00",
      "endTime": "15:00:00",
      "isActive": true
    },
    {
      "dayOfWeek": "FRIDAY",
      "startTime": "09:00:00",
      "endTime": "13:00:00",
      "isActive": true
    }
  ]
}
```

### Response (201 Created)
```json
{
  "status": "success",
  "message": "Successfully created 4 out of 4 availability slots",
  "data": [
    {
      "id": 1,
      "mentorId": "123e4567-e89b-12d3-a456-426614174000",
      "dayOfWeek": "MONDAY",
      "startTime": "09:00:00",
      "endTime": "12:00:00",
      "isActive": true,
      "durationInMinutes": 180,
      "createdAt": "2025-10-11T14:30:00",
      "updatedAt": "2025-10-11T14:30:00"
    },
    {
      "id": 2,
      "mentorId": "123e4567-e89b-12d3-a456-426614174000",
      "dayOfWeek": "MONDAY",
      "startTime": "14:00:00",
      "endTime": "17:00:00",
      "isActive": true,
      "durationInMinutes": 180,
      "createdAt": "2025-10-11T14:30:01",
      "updatedAt": "2025-10-11T14:30:01"
    }
    // ... more slots
  ],
  "timestamp": "2025-10-11T14:30:00"
}
```

### cURL Example
```bash
curl -X POST http://localhost:8080/api/v1/mentor-availability/bulk \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "mentorId": "123e4567-e89b-12d3-a456-426614174000",
    "slots": [
      {
        "dayOfWeek": "MONDAY",
        "startTime": "09:00:00",
        "endTime": "12:00:00"
      },
      {
        "dayOfWeek": "WEDNESDAY",
        "startTime": "14:00:00",
        "endTime": "17:00:00"
      }
    ]
  }'
```

---

## 3. Get Mentor's Weekly Schedule

### Request
```bash
GET /api/v1/mentor-availability/mentor/{mentorId}/weekly?activeOnly=true
```

### Response (200 OK)
```json
{
  "status": "success",
  "message": "Request completed successfully",
  "data": {
    "mentorId": "123e4567-e89b-12d3-a456-426614174000",
    "totalSlots": 8,
    "activeSlots": 7,
    "schedule": [
      {
        "dayOfWeek": "MONDAY",
        "timeSlots": [
          {
            "id": 1,
            "startTime": "09:00:00",
            "endTime": "12:00:00",
            "isActive": true,
            "durationInMinutes": 180
          },
          {
            "id": 2,
            "startTime": "14:00:00",
            "endTime": "17:00:00",
            "isActive": true,
            "durationInMinutes": 180
          }
        ]
      },
      {
        "dayOfWeek": "TUESDAY",
        "timeSlots": [
          {
            "id": 3,
            "startTime": "10:00:00",
            "endTime": "13:00:00",
            "isActive": true,
            "durationInMinutes": 180
          }
        ]
      },
      {
        "dayOfWeek": "WEDNESDAY",
        "timeSlots": [
          {
            "id": 4,
            "startTime": "09:00:00",
            "endTime": "12:00:00",
            "isActive": true,
            "durationInMinutes": 180
          },
          {
            "id": 5,
            "startTime": "13:00:00",
            "endTime": "16:00:00",
            "isActive": true,
            "durationInMinutes": 180
          }
        ]
      },
      {
        "dayOfWeek": "FRIDAY",
        "timeSlots": [
          {
            "id": 6,
            "startTime": "09:00:00",
            "endTime": "13:00:00",
            "isActive": true,
            "durationInMinutes": 240
          }
        ]
      }
    ]
  },
  "timestamp": "2025-10-11T14:30:00"
}
```

### cURL Example
```bash
curl -X GET "http://localhost:8080/api/v1/mentor-availability/mentor/123e4567-e89b-12d3-a456-426614174000/weekly?activeOnly=true" \
  -H "Accept: application/json"
```

---

## 4. Get Mentor Availability for Specific Day

### Request
```bash
GET /api/v1/mentor-availability/mentor/{mentorId}/day/{dayOfWeek}?activeOnly=true
```

### Example
```bash
GET /api/v1/mentor-availability/mentor/123e4567-e89b-12d3-a456-426614174000/day/MONDAY?activeOnly=true
```

### Response (200 OK)
```json
{
  "status": "success",
  "message": "Request completed successfully",
  "data": [
    {
      "id": 1,
      "mentorId": "123e4567-e89b-12d3-a456-426614174000",
      "dayOfWeek": "MONDAY",
      "startTime": "09:00:00",
      "endTime": "12:00:00",
      "isActive": true,
      "durationInMinutes": 180,
      "createdAt": "2025-10-11T14:30:00",
      "updatedAt": "2025-10-11T14:30:00"
    },
    {
      "id": 2,
      "mentorId": "123e4567-e89b-12d3-a456-426614174000",
      "dayOfWeek": "MONDAY",
      "startTime": "14:00:00",
      "endTime": "17:00:00",
      "isActive": true,
      "durationInMinutes": 180,
      "createdAt": "2025-10-11T14:30:00",
      "updatedAt": "2025-10-11T14:30:00"
    }
  ],
  "timestamp": "2025-10-11T14:30:00"
}
```

### cURL Example
```bash
curl -X GET "http://localhost:8080/api/v1/mentor-availability/mentor/123e4567-e89b-12d3-a456-426614174000/day/MONDAY" \
  -H "Accept: application/json"
```

### Valid Day Values
- `MONDAY`
- `TUESDAY`
- `WEDNESDAY`
- `THURSDAY`
- `FRIDAY`
- `SATURDAY`
- `SUNDAY`

---

## 5. Get All Availability for a Mentor

### Request
```bash
GET /api/v1/mentor-availability/mentor/{mentorId}?activeOnly=true
```

### Response (200 OK)
```json
{
  "status": "success",
  "message": "Request completed successfully",
  "data": [
    {
      "id": 1,
      "mentorId": "123e4567-e89b-12d3-a456-426614174000",
      "dayOfWeek": "MONDAY",
      "startTime": "09:00:00",
      "endTime": "12:00:00",
      "isActive": true,
      "durationInMinutes": 180,
      "createdAt": "2025-10-11T14:30:00",
      "updatedAt": "2025-10-11T14:30:00"
    },
    {
      "id": 2,
      "mentorId": "123e4567-e89b-12d3-a456-426614174000",
      "dayOfWeek": "WEDNESDAY",
      "startTime": "14:00:00",
      "endTime": "17:00:00",
      "isActive": true,
      "durationInMinutes": 180,
      "createdAt": "2025-10-11T14:31:00",
      "updatedAt": "2025-10-11T14:31:00"
    }
    // ... more slots
  ],
  "timestamp": "2025-10-11T14:30:00"
}
```

### cURL Example
```bash
curl -X GET "http://localhost:8080/api/v1/mentor-availability/mentor/123e4567-e89b-12d3-a456-426614174000?activeOnly=true" \
  -H "Accept: application/json"
```

---

## 6. Get Authenticated Mentor's Own Availability

### Request
```bash
GET /api/v1/mentor-availability/me?activeOnly=true
Authorization: Bearer <token>
```

### Response (200 OK)
```json
{
  "status": "success",
  "message": "Request completed successfully",
  "data": [
    {
      "id": 1,
      "mentorId": "123e4567-e89b-12d3-a456-426614174000",
      "dayOfWeek": "MONDAY",
      "startTime": "09:00:00",
      "endTime": "12:00:00",
      "isActive": true,
      "durationInMinutes": 180,
      "createdAt": "2025-10-11T14:30:00",
      "updatedAt": "2025-10-11T14:30:00"
    }
    // ... more slots
  ],
  "timestamp": "2025-10-11T14:30:00"
}
```

### cURL Example
```bash
curl -X GET "http://localhost:8080/api/v1/mentor-availability/me?activeOnly=true" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Accept: application/json"
```

---

## 7. Get Authenticated Mentor's Weekly Schedule

### Request
```bash
GET /api/v1/mentor-availability/me/weekly?activeOnly=true
Authorization: Bearer <token>
```

### Response (200 OK)
```json
{
  "status": "success",
  "message": "Request completed successfully",
  "data": {
    "mentorId": "123e4567-e89b-12d3-a456-426614174000",
    "totalSlots": 5,
    "activeSlots": 5,
    "schedule": [
      {
        "dayOfWeek": "MONDAY",
        "timeSlots": [
          {
            "id": 1,
            "startTime": "09:00:00",
            "endTime": "12:00:00",
            "isActive": true,
            "durationInMinutes": 180
          }
        ]
      }
      // ... more days
    ]
  },
  "timestamp": "2025-10-11T14:30:00"
}
```

### cURL Example
```bash
curl -X GET "http://localhost:8080/api/v1/mentor-availability/me/weekly?activeOnly=true" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Accept: application/json"
```

---

## 8. Update an Availability Slot

### Request
```bash
PUT /api/v1/mentor-availability/{id}
Content-Type: application/json
Authorization: Bearer <token>
```

### Request Body (All fields optional)
```json
{
  "dayOfWeek": "TUESDAY",
  "startTime": "10:00:00",
  "endTime": "13:00:00",
  "isActive": true
}
```

### Response (200 OK)
```json
{
  "status": "success",
  "message": "Availability slot updated successfully",
  "data": {
    "id": 1,
    "mentorId": "123e4567-e89b-12d3-a456-426614174000",
    "dayOfWeek": "TUESDAY",
    "startTime": "10:00:00",
    "endTime": "13:00:00",
    "isActive": true,
    "durationInMinutes": 180,
    "createdAt": "2025-10-11T14:30:00",
    "updatedAt": "2025-10-11T14:35:00"
  },
  "timestamp": "2025-10-11T14:35:00"
}
```

### cURL Example
```bash
curl -X PUT http://localhost:8080/api/v1/mentor-availability/1 \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "dayOfWeek": "TUESDAY",
    "startTime": "10:00:00",
    "endTime": "13:00:00"
  }'
```

---

## 9. Toggle Availability Status

### Request
```bash
PATCH /api/v1/mentor-availability/{id}/toggle
Authorization: Bearer <token>
```

### Response (200 OK)
```json
{
  "status": "success",
  "message": "Availability status toggled successfully",
  "data": {
    "id": 1,
    "mentorId": "123e4567-e89b-12d3-a456-426614174000",
    "dayOfWeek": "MONDAY",
    "startTime": "09:00:00",
    "endTime": "12:00:00",
    "isActive": false,
    "durationInMinutes": 180,
    "createdAt": "2025-10-11T14:30:00",
    "updatedAt": "2025-10-11T14:40:00"
  },
  "timestamp": "2025-10-11T14:40:00"
}
```

### cURL Example
```bash
curl -X PATCH http://localhost:8080/api/v1/mentor-availability/1/toggle \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

---

## 10. Get Single Availability Slot by ID

### Request
```bash
GET /api/v1/mentor-availability/{id}
```

### Response (200 OK)
```json
{
  "status": "success",
  "message": "Request completed successfully",
  "data": {
    "id": 1,
    "mentorId": "123e4567-e89b-12d3-a456-426614174000",
    "dayOfWeek": "MONDAY",
    "startTime": "09:00:00",
    "endTime": "12:00:00",
    "isActive": true,
    "durationInMinutes": 180,
    "createdAt": "2025-10-11T14:30:00",
    "updatedAt": "2025-10-11T14:30:00"
  },
  "timestamp": "2025-10-11T14:30:00"
}
```

### cURL Example
```bash
curl -X GET http://localhost:8080/api/v1/mentor-availability/1 \
  -H "Accept: application/json"
```

---

## 11. Delete a Single Availability Slot

### Request
```bash
DELETE /api/v1/mentor-availability/{id}
Authorization: Bearer <token>
```

### Response (200 OK)
```json
{
  "status": "success",
  "message": "Availability slot deleted successfully",
  "timestamp": "2025-10-11T14:45:00"
}
```

### cURL Example
```bash
curl -X DELETE http://localhost:8080/api/v1/mentor-availability/1 \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

---

## 12. Delete All Availability for a Mentor

### Request
```bash
DELETE /api/v1/mentor-availability/mentor/{mentorId}/all
Authorization: Bearer <token>
```

### Response (200 OK)
```json
{
  "status": "success",
  "message": "All availability slots deleted successfully",
  "timestamp": "2025-10-11T14:50:00"
}
```

### cURL Example
```bash
curl -X DELETE http://localhost:8080/api/v1/mentor-availability/mentor/123e4567-e89b-12d3-a456-426614174000/all \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

---

## 13. Delete All Availability for a Specific Day

### Request
```bash
DELETE /api/v1/mentor-availability/mentor/{mentorId}/day/{dayOfWeek}
Authorization: Bearer <token>
```

### Response (200 OK)
```json
{
  "status": "success",
  "message": "Availability slots for MONDAY deleted successfully",
  "timestamp": "2025-10-11T14:55:00"
}
```

### cURL Example
```bash
curl -X DELETE http://localhost:8080/api/v1/mentor-availability/mentor/123e4567-e89b-12d3-a456-426614174000/day/MONDAY \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

---

## Error Responses

### 400 Bad Request - Validation Error
```json
{
  "status": "error",
  "message": "End time must be after start time",
  "timestamp": "2025-10-11T14:30:00"
}
```

### 400 Bad Request - Overlapping Slots
```json
{
  "status": "error",
  "message": "Time slot overlaps with existing availability on MONDAY",
  "timestamp": "2025-10-11T14:30:00"
}
```

### 401 Unauthorized
```json
{
  "timestamp": "2025-10-11T14:30:00",
  "status": 401,
  "error": "Unauthorized",
  "message": "Unauthorized",
  "path": "/api/v1/mentor-availability"
}
```

### 403 Forbidden
```json
{
  "timestamp": "2025-10-11T14:30:00",
  "status": 403,
  "error": "Forbidden",
  "message": "Forbidden",
  "path": "/api/v1/mentor-availability/mentor/123e4567-e89b-12d3-a456-426614174000"
}
```

### 404 Not Found
```json
{
  "status": "error",
  "message": "Availability slot not found",
  "timestamp": "2025-10-11T14:30:00"
}
```

---

## Common Use Cases

### Use Case 1: Set Up Weekly Schedule for New Mentor

```bash
# Step 1: Bulk create all weekly availability
curl -X POST http://localhost:8080/api/v1/mentor-availability/bulk \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "mentorId": "123e4567-e89b-12d3-a456-426614174000",
    "slots": [
      {"dayOfWeek": "MONDAY", "startTime": "09:00:00", "endTime": "12:00:00"},
      {"dayOfWeek": "MONDAY", "startTime": "14:00:00", "endTime": "17:00:00"},
      {"dayOfWeek": "WEDNESDAY", "startTime": "10:00:00", "endTime": "15:00:00"},
      {"dayOfWeek": "FRIDAY", "startTime": "09:00:00", "endTime": "13:00:00"}
    ]
  }'

# Step 2: Verify the schedule
curl -X GET "http://localhost:8080/api/v1/mentor-availability/mentor/123e4567-e89b-12d3-a456-426614174000/weekly" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

### Use Case 2: Mentor Taking a Day Off

```bash
# Temporarily disable all slots for a specific day
# First, get all slots for that day
curl -X GET "http://localhost:8080/api/v1/mentor-availability/mentor/123e4567-e89b-12d3-a456-426614174000/day/MONDAY" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"

# Then toggle each slot (or delete them)
curl -X PATCH http://localhost:8080/api/v1/mentor-availability/1/toggle \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

### Use Case 3: Update Single Time Slot

```bash
# Change a specific time slot
curl -X PUT http://localhost:8080/api/v1/mentor-availability/1 \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "startTime": "10:00:00",
    "endTime": "13:30:00"
  }'
```

### Use Case 4: Display Mentor Schedule on Frontend

```bash
# Get formatted weekly schedule
curl -X GET "http://localhost:8080/api/v1/mentor-availability/mentor/123e4567-e89b-12d3-a456-426614174000/weekly?activeOnly=true" \
  -H "Accept: application/json"
```

---

## Notes

1. **Time Format**: All times use 24-hour format (HH:mm:ss)
2. **UUID Format**: Mentor IDs are standard UUIDs
3. **Query Parameters**:
   - `activeOnly` defaults to `true` if not specified
   - Set to `false` to include inactive slots
4. **Overlap Prevention**: The system automatically prevents overlapping time slots
5. **Authentication**: Most endpoints require JWT token with MENTOR or ADMIN role
6. **Time Validation**: End time must be after start time

---

## Postman Collection

You can import these examples into Postman by creating a new collection with the following structure:

1. Create environment variables:
   - `base_url`: `http://localhost:8080`
   - `jwt_token`: Your actual JWT token
   - `mentor_id`: A valid mentor UUID

2. Use `{{base_url}}`, `{{jwt_token}}`, and `{{mentor_id}}` in your requests

---

## Testing Tips

1. **Start with bulk creation** to quickly set up test data
2. **Use the weekly endpoint** to verify your changes
3. **Test overlap prevention** by creating overlapping slots
4. **Test authentication** with both MENTOR and non-MENTOR users
5. **Test edge cases** like invalid time ranges, non-existent IDs, etc.

---

For more information, visit the Swagger UI documentation at:
```
http://localhost:8080/swagger-ui/index.html
```
