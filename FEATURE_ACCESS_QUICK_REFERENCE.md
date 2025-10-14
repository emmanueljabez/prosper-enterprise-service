# Feature Access Quick Reference

Quick guide for implementing feature-based access control in your code.

## Feature Codes

| Code | Name | Description | Type |
|------|------|-------------|------|
| `NETWORK` | Prosper Mentor Network | Community access | COMMUNITY |
| `YOUTUBE` | YouTube Channel | YouTube content access | CONTENT_ACCESS |
| `LEARN` | Prosper Mentor Learn | Curated content library | CONTENT_ACCESS |
| `SUMMIT` | Prosper Mentor Summit | Summit recordings | EVENT_ACCESS |
| `VIRTUAL_MENTOR` | Virtual Mentor | 24/7 AI mentor | MENTOR_SESSION |
| `ONE_ON_ONE` | 1:1 Sessions | Live mentor sessions | MENTOR_SESSION |

## Common Use Cases

### 1. Check Feature Access in Controller

```java
@GetMapping("/learn/courses")
public ApiResponse<?> getLearnCourses(@AuthenticationPrincipal UUID userId) {
    // Check if user has access to Learn content
    if (!subscriptionService.canAccessFeature(userId, "LEARN")) {
        return ApiResponse.error("Upgrade to Learn tier or higher to access courses");
    }

    // User has access, return courses
    List<Course> courses = courseService.getAllCourses();
    return ApiResponse.success("Courses retrieved", courses);
}
```

### 2. Check Multiple Features

```java
@GetMapping("/summit/recordings")
public ApiResponse<?> getSummitRecordings(@AuthenticationPrincipal UUID userId) {
    if (!subscriptionService.canAccessFeature(userId, "SUMMIT")) {
        return ApiResponse.error("Upgrade to Summit tier or higher to access recordings");
    }

    List<Recording> recordings = summitService.getRecordings();
    return ApiResponse.success("Recordings retrieved", recordings);
}
```

### 3. Get All User Features

```java
@GetMapping("/subscriptions/my-features")
public ApiResponse<?> getMyFeatures(@AuthenticationPrincipal UUID userId) {
    Map<String, Object> features = subscriptionService.getUserFeatures(userId);

    // Returns:
    // {
    //   "tier": "All Access",
    //   "planCode": "ALL_ACCESS",
    //   "features": [
    //     {
    //       "code": "NETWORK",
    //       "name": "Prosper Mentor Network",
    //       "type": "COMMUNITY",
    //       "limit": -1,
    //       "unlimited": true
    //     },
    //     ...
    //   ],
    //   "sessionsRemaining": 1,
    //   "addonSessions": 5,
    //   "allowsAddons": true,
    //   "addonSessionCost": 20.00
    // }

    return ApiResponse.success("Features retrieved", features);
}
```

### 4. Session Booking with Add-ons

```java
@PostMapping("/sessions/book")
public ApiResponse<?> bookSession(
    @AuthenticationPrincipal UUID userId,
    @RequestBody BookSessionRequest request
) {
    // Check if user can book (includes subscription + add-ons)
    if (!subscriptionService.canBookSession(userId)) {
        // Check if they can buy add-ons
        Optional<Subscription> sub = subscriptionService.getActiveSubscription(userId);
        if (sub.isPresent() && sub.get().getPlan().getAllowsAddons()) {
            return ApiResponse.error(
                "No sessions remaining. Purchase extra sessions for $20 each."
            );
        }
        return ApiResponse.error("Upgrade to All Access tier to book 1:1 sessions");
    }

    // Book the session
    Session session = sessionService.bookSession(userId, request);

    // Consume a session (automatically uses add-ons if subscription exhausted)
    subscriptionService.consumeSessionSmart(userId);

    return ApiResponse.success("Session booked", session);
}
```

### 5. Purchase Add-on Sessions

```java
@PostMapping("/subscriptions/addons/purchase")
public ApiResponse<?> purchaseAddonSessions(
    @AuthenticationPrincipal UUID userId,
    @RequestBody PurchaseAddonRequest request
) {
    // Validate user has All Access plan
    if (!subscriptionService.canAccessFeature(userId, "ONE_ON_ONE")) {
        return ApiResponse.error("Add-on sessions are only available for All Access members");
    }

    // Purchase add-on sessions
    ApiResponse<SubscriptionAddon> result = subscriptionService.purchaseAddonSessions(
        userId,
        request.getQuantity(),
        request.getPhoneNumber()
    );

    return result;
}
```

### 6. Display Available Add-ons

```java
@GetMapping("/subscriptions/addons")
public ApiResponse<?> getMyAddons(@AuthenticationPrincipal UUID userId) {
    List<SubscriptionAddon> addons = subscriptionService.getUserAddons(userId);

    // Calculate totals
    int totalRemaining = addons.stream()
        .filter(SubscriptionAddon::hasRemaining)
        .mapToInt(SubscriptionAddon::getRemaining)
        .sum();

    Map<String, Object> response = Map.of(
        "addons", addons,
        "totalRemaining", totalRemaining
    );

    return ApiResponse.success("Add-ons retrieved", response);
}
```

### 7. Feature Check in Service Layer

```java
@Service
public class VirtualMentorService {

    @Autowired
    private SubscriptionService subscriptionService;

    public ApiResponse<ChatResponse> chat(UUID userId, String message) {
        // Check if user has access to virtual mentor
        if (!subscriptionService.canAccessFeature(userId, "VIRTUAL_MENTOR")) {
            return ApiResponse.error(
                "Virtual Mentor is available for Virtual Mentor and All Access tiers. " +
                "Upgrade your subscription to access 24/7 AI mentorship."
            );
        }

        // Process chat
        ChatResponse response = aiService.processMessage(message);
        return ApiResponse.success("Response generated", response);
    }
}
```

### 8. Conditional UI Rendering (Backend for Frontend)

```java
@GetMapping("/dashboard/features")
public ApiResponse<?> getDashboardFeatures(@AuthenticationPrincipal UUID userId) {
    Map<String, Object> features = subscriptionService.getUserFeatures(userId);

    // Add UI-specific flags
    Map<String, Object> uiConfig = new HashMap<>();
    uiConfig.put("showLearnSection", subscriptionService.canAccessFeature(userId, "LEARN"));
    uiConfig.put("showSummitSection", subscriptionService.canAccessFeature(userId, "SUMMIT"));
    uiConfig.put("showVirtualMentor", subscriptionService.canAccessFeature(userId, "VIRTUAL_MENTOR"));
    uiConfig.put("showBookSession", subscriptionService.canAccessFeature(userId, "ONE_ON_ONE"));
    uiConfig.put("canBuyAddons", features.get("allowsAddons"));

    Map<String, Object> response = new HashMap<>();
    response.put("features", features);
    response.put("ui", uiConfig);

    return ApiResponse.success("Dashboard config retrieved", response);
}
```

## Tier Comparison

| Feature | Network | Learn | Summit | Virtual Mentor | All Access |
|---------|---------|-------|--------|----------------|------------|
| NETWORK | ✓ | ✓ | ✓ | ✓ | ✓ |
| YOUTUBE | ✓ | ✓ | ✓ | ✓ | ✓ |
| LEARN | ✗ | ✓ | ✓ | ✓ | ✓ |
| SUMMIT | ✗ | ✗ | ✓ | ✓ | ✓ |
| VIRTUAL_MENTOR | ✗ | ✗ | ✗ | ✓ | ✓ |
| ONE_ON_ONE | ✗ | ✗ | ✗ | ✗ | ✓ (1/mo + add-ons) |

## Testing Feature Access

### Unit Test Example

```java
@Test
public void testLearnUserCanAccessLearnContent() {
    // Given: User with Learn subscription
    UUID userId = createUserWithSubscription("LEARN");

    // When: Check Learn access
    boolean hasAccess = subscriptionService.canAccessFeature(userId, "LEARN");

    // Then: Should have access
    assertTrue(hasAccess);
}

@Test
public void testLearnUserCannotAccessSummit() {
    // Given: User with Learn subscription
    UUID userId = createUserWithSubscription("LEARN");

    // When: Check Summit access
    boolean hasAccess = subscriptionService.canAccessFeature(userId, "SUMMIT");

    // Then: Should not have access
    assertFalse(hasAccess);
}

@Test
public void testAllAccessUserCanUseAddons() {
    // Given: User with All Access subscription, no sessions remaining
    UUID userId = createUserWithSubscription("ALL_ACCESS");
    exhaustSubscriptionSessions(userId);

    // When: Purchase add-on sessions
    ApiResponse<SubscriptionAddon> response =
        subscriptionService.purchaseAddonSessions(userId, 5, "254712345678");

    // Then: Should succeed
    assertTrue(response.isSuccess());

    // And: Can book session with add-on
    assertTrue(subscriptionService.canBookSession(userId));
}
```

## Error Messages by Feature

```java
Map<String, String> FEATURE_ERROR_MESSAGES = Map.of(
    "LEARN", "Upgrade to Learn tier ($5/month) to access curated content",
    "SUMMIT", "Upgrade to Summit tier ($8 one-time) to access recordings",
    "VIRTUAL_MENTOR", "Upgrade to Virtual Mentor tier ($10/month) for 24/7 AI guidance",
    "ONE_ON_ONE", "Upgrade to All Access tier ($30/month) for 1:1 mentor sessions"
);

// Usage
if (!subscriptionService.canAccessFeature(userId, featureCode)) {
    return ApiResponse.error(FEATURE_ERROR_MESSAGES.get(featureCode));
}
```

## Best Practices

### 1. Always Check Features at the Entry Point
```java
// ✓ Good: Check at controller level
@GetMapping("/learn/courses")
public ApiResponse<?> getCourses(@AuthenticationPrincipal UUID userId) {
    if (!subscriptionService.canAccessFeature(userId, "LEARN")) {
        return ApiResponse.error("Access denied");
    }
    return courseService.getCourses();
}

// ✗ Bad: Check deep in business logic
public List<Course> getCourses() {
    if (!hasAccess) { // Too late, should check earlier
        throw new AccessDeniedException();
    }
}
```

### 2. Use Feature Codes, Not Plan Codes
```java
// ✓ Good: Check features
if (subscriptionService.canAccessFeature(userId, "LEARN")) {
    // ...
}

// ✗ Bad: Check plan codes
if (subscription.getPlan().getCode().equals("LEARN")) {
    // Breaks when plans change
}
```

### 3. Provide Upgrade Paths
```java
// ✓ Good: Tell users how to get access
if (!subscriptionService.canAccessFeature(userId, "SUMMIT")) {
    return ApiResponse.error(
        "Upgrade to Summit tier to access recordings",
        Map.of(
            "upgradeUrl", "/subscriptions/upgrade/SUMMIT",
            "cost", "$8 one-time"
        )
    );
}
```

### 4. Use Smart Session Consumption
```java
// ✓ Good: Automatically uses add-ons when needed
subscriptionService.consumeSessionSmart(userId);

// ✗ Bad: Only uses subscription sessions
subscriptionService.consumeSession(userId); // Fails if subscription exhausted
```

## Migration from Old Code

```java
// OLD CODE:
if (subscription.getPlan().getCode().equals("ALL_ACCESS")) {
    // Show feature
}

// NEW CODE:
if (subscriptionService.canAccessFeature(userId, "ONE_ON_ONE")) {
    // Show feature
}
```

## Questions?

- See [DYNAMIC_SUBSCRIPTION_SYSTEM.md](DYNAMIC_SUBSCRIPTION_SYSTEM.md) for full documentation
- See [SubscriptionService.java](src/main/java/com/prosper/prospermentor/service/SubscriptionService.java) for all available methods
- See [IMPLEMENTATION_SUMMARY.md](IMPLEMENTATION_SUMMARY.md) for implementation details
