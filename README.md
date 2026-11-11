# SilinganPH

## Overview

SilinganPH is a community management and issue-reporting platform designed for residential communities such as condominiums, subdivisions, and villages.

The platform enables community administrators, property management offices (PMO), security personnel, maintenance teams, and residents to collaborate through a centralized system for:

- Community announcements
- Community directory management
- Emergency contacts
- Resident issue reporting
- Automated issue routing
- Issue tracking and resolution

---

# Business Workflow

## Phase 1: Client Acquisition & Community Onboarding

### Community Acquisition

Silingan onboard communities such as:

- Condominiums
- Residential subdivisions
- Villages
- Homeowners associations

Process:

1. Silingan engages the community.
2. Community agrees to subscribe.
3. Contract or agreement is finalized.

### Community Creation

Performed by: **Silingan Master Admin**

Actions:

1. Create community account.
2. Assign community name.
3. Configure community profile.
4. Generate unique community code.
5. Create primary Community Admin account.

Example:

```text
Community: Urban Deca Homes Banilad
Community Code: UDHB-2026
Community Admin: PMO Administrator
```

Outcome:

- Community becomes active within SilinganPH.

---

## Phase 2: Staff Management

Performed by: **Community Admin**

Community Admin can create staff accounts with the following roles:

### Community Admin

- Full access

### PMO Staff

- Manage announcements
- Monitor reports

### Security Admin

- Manage security-related reports

### Maintenance Admin

- Manage maintenance-related reports

### Read-Only Staff

- View reports only
- Limited permissions

Outcome:

- Community operations team is onboarded.

---

## Phase 3: Community Launch

Performed by: **Community Admin**

Actions:

- Distribute Community Code
- Introduce SilinganPH to residents
- Provide onboarding instructions

Outcome:

- Residents are invited to join the platform.

---

## Phase 4: Resident Onboarding

### Step 1: Download Application

Resident installs the SilinganPH mobile application.

### Step 2: Join Community

Options:

- Enter Community Code
- Search Community

#### Community Exists

1. Resident selects community.
2. Continues registration.

#### Community Does Not Exist

System displays:

> Your community has not joined Silingan yet.

Resident may submit:

- Community Name
- Community Type
- Location
- Optional Contact Information

Outcome:

- Lead generated for Silingan sales team.

### Step 3: Registration

Required Information:

- Full Name
- Mobile Number

System Actions:

- Sends OTP
- Verifies mobile number

### Step 4: Membership Linking

Resident provides:

- Unit Number
- House Number

System links resident to the selected community.

Outcome:

- Resident becomes an active member.

---

## Phase 5: Daily Community Operations

Residents can access:

- Announcements
- Community Directory
- Emergency Contacts
- Report an Issue
- My Reports

---

## Phase 6: Announcements

Performed by:

- Community Admin
- PMO Staff

### Create Announcement

Fields:

- Title
- Description
- Optional Image

Examples:

- Water Interruption
- PMO Closure
- Maintenance Schedule
- Holiday Advisory
- Security Advisory

### System Actions

- Publish announcement
- Send resident notifications

### Resident Actions

- View announcement
- Acknowledge announcement
- Mark announcement as helpful

Outcome:

- Official communication channel for the community.

---

## Phase 7: Directory & Emergency Contacts

Managed by: **Community Admin**

### Community Services

- PMO
- Security
- Maintenance

### Emergency Services

- Police
- Fire Department
- Ambulance

### Utility Providers

- Water Provider
- Electricity Provider
- Internet Provider

Resident Actions:

- View contact information
- Call directly

Outcome:

- Centralized information repository.

---

## Phase 8: Issue Reporting

Performed by: **Resident**

### Available Categories

- Maintenance & Utilities
- Cleanliness & Waste
- Parking Issues
- Security & Safety
- Noise Complaints
- Access & Entry
- Other

### Report Details

Required:

- Description

Optional:

- Photo
- Location

### System Actions

Upon submission:

1. Generate Report ID
2. Record timestamp
3. Link report to resident
4. Link report to community
5. Set status to `SUBMITTED`

Outcome:

- Issue report is created.

---

## Phase 9: Automated Report Routing

The system automatically assigns the report.

| Category | Assigned Team |
|-----------|--------------|
| Maintenance & Utilities | Maintenance Admin |
| Cleanliness & Waste | Maintenance Admin |
| Parking Issues | Security Admin |
| Noise Complaints | Security Admin |
| Security & Safety | Security Admin |
| Access & Entry | Security Admin |
| Other | PMO Review Queue |

System Actions:

- Assign report
- Notify responsible team
- Notify Community Admin

Outcome:

- Report reaches the appropriate team automatically.

---

## Phase 10: Report Management

Performed by Assigned Team.

### Available Actions

- Accept Report
- Update Status
- Add Notes
- Add Resolution Notes

### Status Workflow

```text
SUBMITTED
    ↓
ASSIGNED
    ↓
IN_PROGRESS
    ↓
RESOLVED
```

### Example: Parking Issue

```text
SUBMITTED
    ↓
Guard Notified
    ↓
Warning Issued
    ↓
Grace Period
    ↓
RESOLVED
```

### Example: Security Concern

```text
SUBMITTED
    ↓
Security Review
    ↓
CCTV Review
    ↓
RESOLVED
```

Outcome:

- Issue is actively managed until completion.

---

## Phase 11: Resident Report Tracking

Residents can access:

### My Reports

Information displayed:

- Report ID
- Category
- Status
- Date Submitted
- Priority

### Report Details

- Description
- Photos
- Location
- Current Status
- Timeline

Example Timeline:

```text
✓ Report Submitted
✓ Assigned to Maintenance
🔵 In Progress
○ Resolved
```

Outcome:

- Full visibility and transparency for residents.

---

## Phase 12: Issue Resolution

Performed by Assigned Team.

### Resolution Process

1. Team marks report as resolved.
2. Resolution timestamp is recorded.
3. Status is updated.
4. Resident is notified.

Final Timeline:

```text
✓ Submitted
✓ Assigned
✓ In Progress
✓ Resolved
```

Outcome:

- Issue is closed.
- Historical records are retained.
- Resolution remains visible to residents.

---

# User Roles

## Silingan Master Admin

Responsibilities:

- Create communities
- Manage subscriptions
- Manage community administrators

## Community Admin

Responsibilities:

- Configure community settings
- Manage staff
- Manage directory
- Publish announcements
- Monitor all reports

## PMO Staff

Responsibilities:

- Monitor reports
- Publish announcements
- Handle uncategorized issues

## Security Admin

Responsibilities:

- Manage security, parking, access, and noise-related reports

## Maintenance Admin

Responsibilities:

- Manage maintenance and utility-related reports

## Resident

Responsibilities:

- Join communities
- View announcements
- Access directory
- Submit reports
- Track issue resolution

---

# MVP Scope (Phase 1)

The minimum viable product includes:

- Community onboarding
- Resident registration
- Community code joining
- Announcement management
- Community directory
- Emergency contacts
- Issue reporting
- Automated routing
- Report tracking
- Status management
- Notifications