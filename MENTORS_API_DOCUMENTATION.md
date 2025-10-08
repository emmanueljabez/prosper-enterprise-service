# Mentors API Documentation

## Overview

This document describes the new API endpoints for retrieving mentor profiles from the system.

## Endpoints

### 1. Get All Mentors (Complete Profile)

**Endpoint:** `GET /api/profiles/mentors`

**Description:** Returns all profiles where the role is "MENTOR" with complete profile information including mentor-specific details.

**Authentication:** Public endpoint (no authentication required)

**Response Format:**
```json
{
  "mentors": [
    {
      "id": "12345678-90ab-cdef-1234-567890abcdef",
      "email": "mentor@example.com",
      "firstName": "John",
      "lastName": "Smith",
      "avatarUrl": "https://example.com/avatar.jpg",
      "bio": "Experienced software engineer with 10+ years in tech",
      "phone": "+1-555-0123",
      "location": "San Francisco, CA",
      "role": "MENTOR",
      "isVerified": true,
      "createdAt": "2024-01-01T00:00:00Z",
      "updatedAt": "2024-01-01T00:00:00Z",
      "expertise": ["JavaScript", "Python", "Software Architecture"],
      "interests": ["Machine Learning", "Startups"],
      "dob": "1990-01-01",
      "gender": "Male",
      "industry": "Technology",
      "howDidYouKnowAboutUs": "LinkedIn",
      "linkedinUrl": "https://linkedin.com/in/johnsmith",
      "favouriteQuote": "Code is poetry",
      "country": "USA",
      "mentorProfile": {
        "id": "12345678-90ab-cdef-1234-567890abcdef",
        "title": "Senior Software Engineer",
        "company": "Tech Corp",
        "yearsExperience": 10,
        "hourlyRate": 150.00,
        "specializations": ["Backend Development", "System Design"],
        "languages": ["English", "Spanish"],
        "timezone": "PST",
        "availabilityHours": "9AM-5PM",
        "totalSessions": 25,
        "rating": 4.8,
        "totalReviews": 12,
        "isAvailable": true,
        "bio": "Passionate about helping others grow in tech",
        "avatarUrl": "https://example.com/mentor-avatar.jpg",
        "createdAt": "2024-01-01T00:00:00Z",
        "updatedAt": "2024-01-01T00:00:00Z"
      }
    }
  ],
  "count": 1
}
```

### 2. Get All Mentors (Basic Profile Only)

**Endpoint:** `GET /api/profiles/mentors/basic`

**Description:** Returns all profiles where the role is "MENTOR" with only basic profile information (no mentor-specific details).

**Authentication:** Public endpoint (no authentication required)

**Response Format:**
```json
{
  "mentors": [
    {
      "id": "12345678-90ab-cdef-1234-567890abcdef",
      "email": "mentor@example.com",
      "firstName": "John",
      "lastName": "Smith",
      "avatarUrl": "https://example.com/avatar.jpg",
      "bio": "Experienced software engineer with 10+ years in tech",
      "phone": "+1-555-0123",
      "location": "San Francisco, CA",
      "role": "MENTOR",
      "isVerified": true,
      "createdAt": "2024-01-01T00:00:00Z",
      "updatedAt": "2024-01-01T00:00:00Z",
      "expertise": ["JavaScript", "Python", "Software Architecture"],
      "interests": ["Machine Learning", "Startups"],
      "dob": "1990-01-01",
      "gender": "Male",
      "industry": "Technology",
      "howDidYouKnowAboutUs": "LinkedIn",
      "linkedinUrl": "https://linkedin.com/in/johnsmith",
      "favouriteQuote": "Code is poetry",
      "country": "USA"
    }
  ],
  "count": 1
}
```

## Error Responses

### Database Connection Error
```json
{
  "error": "Failed to fetch mentors"
}
```

### General Server Error
```json
{
  "error": "Internal server error message"
}
```

## Implementation Details

### Repository Layer
- Uses existing `ProfileRepository.findByRole("MENTOR")` method
- Leverages Spring Data JPA for database operations

### Service Layer
- `ProfileService.getAllMentors()`: Returns complete mentor profiles with mentor-specific details
- `ProfileService.getAllMentorProfiles()`: Returns basic mentor profiles only

### Controller Layer
- `ProfileController.getAllMentors()`: Handles `/api/profiles/mentors` endpoint
- `ProfileController.getAllMentorProfiles()`: Handles `/api/profiles/mentors/basic` endpoint

### Security Configuration
- Both endpoints are configured as public in `SecurityConfig.java`
- No authentication required to browse mentors

## Usage Examples

### Using curl
```bash
# Get all mentors with complete profiles
curl -X GET "http://localhost:8080/api/profiles/mentors" \
  -H "Content-Type: application/json"

# Get all mentors with basic profiles only
curl -X GET "http://localhost:8080/api/profiles/mentors/basic" \
  -H "Content-Type: application/json"
```

### Using JavaScript fetch
```javascript
// Get all mentors with complete profiles
fetch('/api/profiles/mentors')
  .then(response => response.json())
  .then(data => {
    console.log(`Found ${data.count} mentors:`, data.mentors);
  });

// Get all mentors with basic profiles only
fetch('/api/profiles/mentors/basic')
  .then(response => response.json())
  .then(data => {
    console.log(`Found ${data.count} mentors:`, data.mentors);
  });
```

## Database Requirements

The API requires:
1. A working connection to the Supabase PostgreSQL database
2. The `profiles` table with mentor records (role = 'MENTOR')
3. Optionally, the `mentor_profiles` table for mentor-specific details

## Notes

- The endpoints return all mentors without pagination. Consider adding pagination for large datasets.
- The complete profile endpoint includes mentor-specific details if available in the `mentor_profiles` table.
- Both endpoints are public to allow browsing of mentors without authentication.
- The API follows the existing patterns in the codebase for error handling and response formatting.

