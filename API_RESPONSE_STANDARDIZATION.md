# 🔄 API Response Standardization & Variable Session Duration

## 📋 **Overview**

The Session Booking API has been enhanced with:
1. **Standard API Response Format** - Consistent response structure across all POST endpoints
2. **Variable Session Duration** - Support for sessions from 30 minutes to 4 hours
3. **KES Currency** - Changed from USD to Kenyan Shilling (KES)

---

## ✅ **What Was Implemented**

### **1. Standard API Response Wrapper** 📦
**File**: `src/main/java/com/prosper/prospermentor/controller/dto/ApiResponse.java`

**Features**:
```java
public class ApiResponse<T> {
    private String status;           // "success", "error", "validation_error"
    private String message;          // Human-readable message
    private T data;                  // Response payload
    private ErrorDetails error;      // Error details (when applicable)
    private LocalDateTime timestamp; // Response timestamp
    private String requestId;        // Optional tracking ID
    private PaginationMeta pagination; // For paginated responses
}
```

**Static Factory Methods**:
- `ApiResponse.success(data)` - Success with data
- `ApiResponse.success(data, message)` - Success with custom message
- `ApiResponse.error(message)` - Error response
- `ApiResponse.validationError(message, details)` - Validation errors

### **2. Updated Session Controller** 🎯
**File**: `src/main/java/com/prosper/prospermentor/controller/SessionController.java`

**All POST endpoints now return**:
```java
ResponseEntity<ApiResponse<SessionResponseDto>>
```

**Response Examples**:

**✅ Success Response (201 Created)**:
```json
{
  "status": "success",
  "message": "Session booking created successfully",
  "data": {
    "id": "session-uuid",
    "mentorId": "mentor-uuid",
    "menteeId": "mentee-uuid",
    "scheduledStart": "2024-01-15T10:00:00Z",
    "scheduledEnd": "2024-01-15T11:30:00Z",
    "status": "PENDING",
    "meetingPlatform": "GOOGLE_MEET",
    "price": 2500.00,
    "currency": "KES",
    "paymentStatus": "PENDING"
  },
  "timestamp": "2024-01-15T09:30:00"
}
```

**❌ Error Response (400 Bad Request)**:
```json
{
  "status": "error",
  "message": "Session must be at least 30 minutes",
  "timestamp": "2024-01-15T09:30:00"
}
```

### **3. Variable Session Duration** ⏰
**File**: `src/main/java/com/prosper/prospermentor/controller/dto/SessionDtos.java`

**Request DTO includes both start and end times**:
```java
public static class CreateSessionRequestDto {
    @NotNull private UUID mentorId;
    @NotNull private UUID menteeId;
    @NotNull private UUID skillId;
    @NotNull private ZonedDateTime scheduledStart;
    @NotNull private ZonedDateTime scheduledEnd;    // ✅ Required field
    @NotNull private Session.MeetingPlatform meetingPlatform;
    private String menteeMessage;
}
```

**Validation Rules**:
- **Minimum Duration**: 30 minutes
- **Maximum Duration**: 4 hours  
- **Advance Booking**: At least 1 hour in advance
- **Time Validation**: `scheduledStart` must be before `scheduledEnd`

### **4. Currency Change** 💰
**File**: `src/main/java/com/prosper/prospermentor/service/SessionBookingService.java`

```java
// Changed from USD to KES
session.setCurrency("KES");
```

---

## 🔧 **Updated Endpoints**

### **POST /api/v1/sessions/book**
- ✅ Returns `ApiResponse<SessionResponseDto>`
- ✅ Supports variable duration (30 min - 4 hours)
- ✅ Uses KES currency
- ✅ Comprehensive error handling

### **POST /api/v1/sessions/{id}/confirm**
- ✅ Returns `ApiResponse<SessionResponseDto>`
- ✅ Standard success/error responses

### **POST /api/v1/sessions/{id}/cancel**
- ✅ Returns `ApiResponse<SessionResponseDto>`
- ✅ Consistent response format

### **PUT /api/v1/sessions/{id}/status**
- ✅ Returns `ApiResponse<SessionResponseDto>`
- ✅ Admin-only endpoint with standard responses

---

## 📝 **Example API Usage**

### **Book a 1.5-Hour Session**
```bash
curl -X POST "http://localhost:8080/api/v1/sessions/book" \\
  -H "Authorization: Bearer your-jwt-token" \\
  -H "Content-Type: application/json" \\
  -d '{
    "mentorId": "123e4567-e89b-12d3-a456-426614174000",
    "menteeId": "123e4567-e89b-12d3-a456-426614174001", 
    "skillId": "123e4567-e89b-12d3-a456-426614174002",
    "scheduledStart": "2024-01-15T10:00:00Z",
    "scheduledEnd": "2024-01-15T11:30:00Z",
    "meetingPlatform": "GOOGLE_MEET",
    "menteeMessage": "Excited to learn advanced React patterns!"
  }'
```

### **Expected Response**
```json
{
  "status": "success",
  "message": "Session booking created successfully",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "mentorId": "123e4567-e89b-12d3-a456-426614174000",
    "menteeId": "123e4567-e89b-12d3-a456-426614174001",
    "skillId": "123e4567-e89b-12d3-a456-426614174002",
    "title": "Mentorship Session: React Development",
    "scheduledStart": "2024-01-15T10:00:00Z",
    "scheduledEnd": "2024-01-15T11:30:00Z",
    "status": "PENDING",
    "meetingPlatform": "GOOGLE_MEET",
    "price": 3750.00,
    "currency": "KES",
    "paymentStatus": "PENDING",
    "menteeMessage": "Excited to learn advanced React patterns!"
  },
  "timestamp": "2024-01-15T09:30:15"
}
```

---

## 🎯 **Benefits**

### **1. Consistent API Experience**
- All POST endpoints follow the same response pattern
- Predictable error handling
- Easy to parse and handle in frontend applications

### **2. Flexible Session Duration**
- Mentees can book sessions based on their learning needs
- 30-minute quick sessions for specific questions
- 4-hour intensive sessions for complex topics
- Better pricing flexibility for mentors

### **3. Local Currency Support**
- Pricing in Kenyan Shilling (KES) for local market
- Better user experience for Kenyan users
- Clearer pricing understanding

### **4. Enhanced Error Handling**
- Detailed validation messages
- Structured error responses
- Easier debugging and troubleshooting

---

## 🚀 **Ready for Production**

The mentorship platform now features:
- ✅ **Standardized API responses** for all POST operations
- ✅ **Flexible session durations** (30 min - 4 hours)
- ✅ **Local currency support** (KES)
- ✅ **Google Meet integration** as primary platform
- ✅ **Comprehensive validation** and error handling
- ✅ **Full compilation** and build success

**Next Steps**: Deploy and test with real user scenarios! 🎉


