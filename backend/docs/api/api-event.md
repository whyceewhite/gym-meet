# Event API

Base path: `/event`

Manages gymnastics competitions where `Event` is the named competition (e.g.
"2026 European Championships"). An `Event` is the primary record to which `Participant`s and `Session`s are related.

---

## Endpoints

### GET /event/{id}

Returns a single event by ID.

**Path Parameters**

| Parameter | Type | Description |
|-----------|------|-------------|
| id | Long | Event ID |

**Responses**

| Status | Description |
|--------|-------------|
| 200 OK | Event found; returns [EventResponse](#eventresponse-object) |
| 404 Not Found | No event with the given ID |

---

### GET /event

Returns a paginated list of events.

**Query Parameters**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| page | int | 0 | Zero-based page index |
| size | int | 10 | Number of records per page |

**Responses**

| Status | Description |
|--------|-------------|
| 200 OK | Paginated result; returns a Spring Data `Page` containing a list of [EventResponse](#eventresponse-object) objects |

---

### POST /event

Creates a new event.

**Request Body** — [EventRequest](#eventrequest-object) (JSON)

**Validation**

- When pressent, the `figEventNumber` must be unique across events.
- When `figEventNumber` is absent, the pair `title` + `startDate` must be unique.

**Responses**

| Status | Description |
|--------|-------------|
| 201 Created | Event created; returns the new [EventResponse](#eventresponse-object) |
| 409 Conflict | An event with the same `figEventNumber` (or `title` + `startDate`, when `figEventNumber` is absent) already exists |

---

### PUT /event/{id}

Updates an existing event. Replaces `figEventNumber`, `title`, `startDate`,
`endDate`, `city`, `countryCode`, `venue`, `disciplines`, and `level`.

**Path Parameters**

| Parameter | Type | Description |
|-----------|------|-------------|
| id | Long | Event ID |

**Request Body** — [EventRequest](#eventrequest-object) (JSON)

**Validation**

- Same duplicate check as create: the update is rejected if it would make this
  event's `figEventNumber` (or `title` + `startDate`, when `figEventNumber` is
  absent) match a *different* existing event.

**Responses**

| Status | Description |
|--------|-------------|
| 200 OK | Event updated; returns the updated [EventResponse](#eventresponse-object) |
| 404 Not Found | No event with the given ID |
| 409 Conflict | The update would duplicate another existing event's `figEventNumber` (or `title` + `startDate`) |

---

### DELETE /event/{id}

Deletes an event by ID.

**Path Parameters**

| Parameter | Type | Description |
|-----------|------|-------------|
| id | Long | Event ID |

**Responses**

| Status | Description |
|--------|-------------|
| 204 No Content | Event deleted |
| 404 Not Found | No event with the given ID |
| 409 Conflict | The event still has dependent `Session` or `Participant` records and cannot be deleted |

---

## Data Objects

### EventResponse Object

Returned by all endpoints above.

| Field | Type | Description |
|-------|------|-------------|
| id | Long | Unique identifier. |
| figEventNumber | String | FIG-assigned event code, or no value non FIG-assigned events |
| title | String | Event name |
| startDate | LocalDate | First day of competition (ISO 8601, e.g. `2026-04-20`) |
| endDate | LocalDate | Last day of competition (ISO 8601, e.g. `2026-04-26`) |
| city | String | Host city |
| countryCode | String | Host country code represented by the 3-character ISO-3166 value |
| venue | String | Venue or arena name where the event is hosted and `null` if not known |
| disciplines | Array of String | Discipline codes this event includes: `MAG`, `WAG` |
| level | String | Competitive tier code of the event (e.g. `CONTINENTAL_CHAMPIONSHIPS`, `WORLD_CUP`) or `UNKNOWN` if not known |
| creationTimestamp | Instant | ISO 8601 UTC timestamp of record creation |
| lastUpdateTimestamp | Instant | ISO 8601 UTC timestamp of last update |

**Example**

```json
{
  "id": 7,
  "figEventNumber": "FIG-2026-EURO",
  "title": "2026 European Championships",
  "startDate": "2026-04-20",
  "endDate": "2026-04-26",
  "city": "Stuttgart",
  "countryCode": "DEU",
  "venue": "Schleyerhalle",
  "disciplines": ["MAG", "WAG"],
  "level": "CONTINENTAL_CHAMPIONSHIPS",
  "creationTimestamp": "2026-01-05T09:00:00Z",
  "lastUpdateTimestamp": "2026-01-05T09:00:00Z"
}
```

---

### EventRequest Object

Request body for `POST /event` and `PUT /event/{id}`.

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| figEventNumber | String | No | Max 20 characters | FIG-assigned event code, or no value non FIG-assigned events |
| title | String | Yes | Max 200 characters | Event name |
| startDate | LocalDate | Yes | ISO 8601 | First day of competition |
| endDate | LocalDate | Yes | ISO 8601 | Last day of competition |
| city | String | Yes | Max 100 characters | Host city |
| countryCode | String | Yes | 3-character ISO-3166 code | Host country |
| venue | String | No | Max 150 characters | Venue/arena name |
| disciplines | Array of String | Yes | At least one of: `MAG`, `WAG` | Discipline(s) this event includes |
| level | String | Yes | `EventLevel` enum code | Competitive tier code of the event (e.g. `CONTINENTAL_CHAMPIONSHIPS`, `WORLD_CUP`) or `UNKNOWN` if not known |

**Duplicate check:** `figEventNumber` (when present), otherwise `title` + `startDate`

**Example**

```json
{
  "figEventNumber": "FIG-2026-EURO",
  "title": "2026 European Championships",
  "startDate": "2026-04-20",
  "endDate": "2026-04-26",
  "city": "Stuttgart",
  "countryCode": "DEU",
  "venue": "Schleyerhalle",
  "disciplines": ["MAG", "WAG"],
  "level": "CONTINENTAL_CHAMPIONSHIPS"
}
```
