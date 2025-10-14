# Circular Reference Fix - JSON Serialization

## Problem

The API was returning infinite nested objects due to circular references in the entity relationships:

```
SubscriptionPlan → planFeatures → PlanFeature → plan → planFeatures → ...
```

This caused massive JSON responses with repeated data.

## Solution

Applied Jackson annotations to break the circular reference:

### 1. SubscriptionPlan.java
Added `@JsonManagedReference` on the `planFeatures` collection:

```java
@OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
@JsonManagedReference  // ← Added this
private List<PlanFeature> planFeatures = new ArrayList<>();
```

### 2. PlanFeature.java
Added `@JsonBackReference` on the `plan` field:

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "plan_id", nullable = false)
@JsonBackReference  // ← Added this
private SubscriptionPlan plan;
```

### 3. SubscriptionAddon.java
Added `@JsonIgnoreProperties` on the `subscription` field:

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "subscription_id", nullable = false)
@JsonIgnoreProperties({"plan", "addons", "hibernateLazyInitializer", "handler"})  // ← Added this
private Subscription subscription;
```

## How It Works

### @JsonManagedReference / @JsonBackReference
- **Managed side** (parent): Serializes normally with `@JsonManagedReference`
- **Back side** (child): Excluded from serialization with `@JsonBackReference`

This creates a one-way relationship in JSON:
```
SubscriptionPlan → planFeatures (serialized)
PlanFeature → plan (NOT serialized, breaking the loop)
```

### @JsonIgnoreProperties
Ignores specific properties when serializing the related object, preventing lazy loading issues and circular references.

## Result

### Before (Infinite Nesting):
```json
{
  "plan": {
    "planFeatures": [
      {
        "plan": {
          "planFeatures": [
            {
              "plan": {
                // ... infinite recursion
              }
            }
          ]
        }
      }
    ]
  }
}
```

### After (Clean Structure):
```json
{
  "id": "uuid",
  "name": "Learn",
  "code": "LEARN",
  "cost": 5.00,
  "planFeatures": [
    {
      "id": "uuid",
      "feature": {
        "code": "NETWORK",
        "name": "Prosper Mentor Network"
      },
      "limitValue": -1,
      "enabled": true
    },
    {
      "id": "uuid",
      "feature": {
        "code": "LEARN",
        "name": "Prosper Mentor Learn"
      },
      "limitValue": -1,
      "enabled": true
    }
  ]
}
```

## Testing

Test the fix by calling:
```bash
curl "http://localhost:8080/api/v1/subscriptions/plans"
```

You should now see clean JSON without infinite nesting.

## Related Files

- [SubscriptionPlan.java](src/main/java/com/prosper/prospermentor/entity/SubscriptionPlan.java:135)
- [PlanFeature.java](src/main/java/com/prosper/prospermentor/entity/PlanFeature.java:38)
- [SubscriptionAddon.java](src/main/java/com/prosper/prospermentor/entity/SubscriptionAddon.java:38)

## Additional Notes

- Lazy loading is preserved (FetchType.LAZY)
- Hibernate proxies are handled with `hibernateLazyInitializer` and `handler` in `@JsonIgnoreProperties`
- The relationship is maintained in the database; only JSON serialization is affected
