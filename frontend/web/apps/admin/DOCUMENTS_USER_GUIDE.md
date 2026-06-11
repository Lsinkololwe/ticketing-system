# Document Verification - Admin User Guide

## Overview

The Document Verification page allows platform administrators to review and approve verification documents uploaded by event organizers. This is a critical step in the organizer onboarding workflow, ensuring that all organizations meet regulatory and compliance requirements before they can publish events and receive payouts.

## Accessing the Page

### Navigation
1. Log in to the Admin Portal
2. In the left sidebar, find "Action Center"
3. Click "Document Verification"
4. The page will display all pending documents

### Direct URL
`https://admin.ticketing.pml.com/documents`

### Roles Required
- **SUPER_ADMIN**: Full access
- **ADMIN**: Full access
- **FINANCE**: No access (operations-only role)

## Page Layout

### Header Section
- **Title**: "Document Verification"
- **Description**: Brief explanation of the page purpose
- **Refresh Button**: Reload the document queue

### Filter Section
- **Search Bar**: Search by file name or document type
- **Type Filter**: Dropdown to filter by specific document types

### Statistics Section
- **Total Pending**: Number of documents awaiting review
- **Document Types**: Number of unique document types in queue

### Documents Grid
- Responsive grid displaying all pending documents
- Cards show thumbnail, metadata, and action buttons

## Document Types

The system supports the following verification document types:

| Document Type | Purpose | Required For |
|--------------|---------|--------------|
| **ID_DOCUMENT** | National ID or Passport | All organizers |
| **BUSINESS_LICENSE** | Business registration certificate | Business organizations |
| **TAX_CERTIFICATE** | TPIN certificate from ZRA | All organizers |
| **BANK_STATEMENT** | Bank account verification | Payout setup |
| **PROOF_OF_ADDRESS** | Utility bill or lease agreement | Address verification |
| **ARTICLES_OF_INCORPORATION** | Company incorporation documents | Limited companies |
| **OTHER** | Additional supporting documents | Case-by-case |

## Reviewing Documents

### Step 1: Browse Pending Queue

When you open the page, you'll see all documents with **PENDING** status:

```
┌─────────────────────────────────────────────┐
│ [Image Thumbnail]                           │
│                                             │
│ National ID / Passport         [PENDING]   │
│ national_id_12345.jpg                       │
│                                             │
│ 📍 ABC Events Ltd                          │
│ 📅 Jan 15, 2026, 10:30 AM                 │
│ 📄 2.5 MB                                  │
│                                             │
│ [Preview] [✓ Approve] [✗ Reject]          │
└─────────────────────────────────────────────┘
```

### Step 2: Filter Documents (Optional)

**By Search**:
- Type in the search bar to filter by file name or document type
- Example: "national" will show all National ID documents

**By Type**:
- Click the type filter dropdown
- Select a specific document type
- Example: "Business License" to review only licenses

### Step 3: Preview Document

To examine a document in detail:

1. Click the **Preview** button or click the thumbnail
2. A full-screen dialog will open
3. Review the document:
   - **Images**: Full-size image viewer
   - **PDFs**: Embedded PDF reader
   - **Other files**: Download prompt

4. Check the metadata:
   - File name
   - File size
   - Upload timestamp

5. Click **Download** if you need to save the file
6. Click **Close** to return to the list

### Step 4: Make a Decision

After reviewing the document, you must either approve or reject it.

#### Approve Document

✅ **When to Approve**:
- Document is clear and legible
- Information matches organization details
- Document is current (not expired)
- Document type is correct
- All required information is visible

**How to Approve**:
1. Click the green **Approve** button
2. Confirm the action in the dialog
3. Document status changes to **APPROVED**
4. Document is removed from pending queue
5. Organizer is notified (automatic)

#### Reject Document

❌ **When to Reject**:
- Document is blurry or illegible
- Information doesn't match organization details
- Document is expired
- Wrong document type uploaded
- Missing required information
- Suspected fraudulent document

**How to Reject**:
1. Click the red **Reject** button
2. Enter a detailed rejection reason (required)
3. Confirm the action
4. Document status changes to **REJECTED**
5. Document is removed from pending queue
6. Organizer receives notification with your reason

**Rejection Reason Guidelines**:
- Be specific about what's wrong
- Provide clear instructions for correction
- Use professional, respectful language

Examples:
- ✅ Good: "The National ID image is too blurry to read the ID number. Please upload a clearer photo."
- ✅ Good: "This Tax Certificate expired on Dec 31, 2025. Please upload a current TPIN certificate."
- ❌ Poor: "Bad quality"
- ❌ Poor: "Not acceptable"

## Document Verification Checklist

Use this checklist when reviewing documents:

### All Documents
- [ ] Image/PDF is clear and legible
- [ ] All text is readable
- [ ] Document is complete (not cut off)
- [ ] File format is appropriate (JPEG, PNG, PDF)
- [ ] File is not corrupted

### National ID / Passport
- [ ] Photo is clear and matches organizer (if known)
- [ ] ID number is visible and readable
- [ ] Full name matches organization owner name
- [ ] Expiry date is valid (not expired)
- [ ] ID appears authentic (no signs of tampering)

### Business License
- [ ] Business name matches organization name
- [ ] Registration number is visible
- [ ] Issue date and expiry date are visible
- [ ] License is current (not expired)
- [ ] Issuing authority is clearly stated

### Tax Certificate (TPIN)
- [ ] TPIN number is clearly visible
- [ ] Business name matches organization name
- [ ] Certificate is issued by ZRA
- [ ] Certificate is current (issued within last 2 years)

### Bank Statement
- [ ] Account holder name matches organization name
- [ ] Account number is visible
- [ ] Bank name is clearly stated
- [ ] Statement is recent (within last 3 months)
- [ ] Shows at least 3 months of transactions

### Proof of Address
- [ ] Address matches organization business address
- [ ] Document is recent (within last 3 months)
- [ ] Name matches organization name
- [ ] Document type is acceptable (utility bill, lease)

### Articles of Incorporation
- [ ] Company name matches organization name
- [ ] Registration number is visible
- [ ] Directors/shareholders are listed
- [ ] Document is stamped/certified
- [ ] All pages are included

## Workflow Examples

### Example 1: Quick Approval

**Scenario**: Reviewing a clear, valid National ID

1. Open Document Verification page
2. See document card for "National ID / Passport"
3. Click **Preview** to view full-size image
4. Verify:
   - Image is clear
   - ID number is readable: 123456/78/9
   - Name matches: John Musonda
   - Expiry date: 2028 (valid)
5. Click **Close**
6. Click **Approve**
7. Confirm approval
8. ✅ Document approved in 30 seconds

### Example 2: Rejection with Reason

**Scenario**: Business License is expired

1. Open Document Verification page
2. Filter by "Business License"
3. Click **Preview** on document
4. Notice expiry date: December 31, 2023
5. Click **Reject**
6. Enter reason: "This Business License expired on December 31, 2023. Please upload a current license or evidence of renewal application."
7. Confirm rejection
8. ✅ Document rejected with clear instructions

### Example 3: Bulk Review Session

**Scenario**: Reviewing 10 documents in one session

1. Open Document Verification page
2. Note total pending: 12 documents
3. Set aside 20-30 minutes for focused review
4. Review each document:
   - Preview → Decision → Next
5. Use search to find specific organizer if needed
6. Track progress via "Total Pending" counter
7. Refresh page to see updated queue
8. ✅ Completed 10 reviews in 25 minutes

## Impact of Decisions

### When You Approve a Document

1. **Document Status**: PENDING → APPROVED
2. **Verification Metadata**: System records:
   - Verified by: Your admin user ID
   - Verified at: Current timestamp
3. **Organization Status**: If all required documents are approved:
   - `documentsVerified` flag set to `true`
   - Organization may become eligible for full approval
4. **Organizer Notification**: Automatic email/SMS sent:
   - "Your [Document Type] has been verified"
5. **Next Steps**: Organizer can proceed with onboarding

### When You Reject a Document

1. **Document Status**: PENDING → REJECTED
2. **Rejection Metadata**: System records:
   - Rejection reason: Your provided message
   - Rejected at: Current timestamp
3. **Organization Status**:
   - `documentsVerified` remains `false`
   - Organization cannot be fully approved
4. **Organizer Notification**: Automatic email/SMS sent:
   - "Your [Document Type] was not approved"
   - Includes your rejection reason
   - Provides resubmission instructions
5. **Next Steps**: Organizer must upload a corrected document

## Best Practices

### Document Review Guidelines

1. **Be Thorough**: Take time to review each document carefully
2. **Be Consistent**: Apply the same standards to all organizers
3. **Be Fair**: Don't discriminate based on organization size or type
4. **Be Clear**: Provide specific, actionable rejection reasons
5. **Be Prompt**: Review documents within 24-48 hours

### Red Flags to Watch For

⚠️ **Potential Fraud Indicators**:
- Photo manipulation or editing artifacts
- Inconsistent fonts or alignment
- Mismatched colors or resolution
- Information doesn't match across documents
- Expired or invalid document numbers
- Documents from unrecognized issuers

**Action**: If you suspect fraud:
1. **Reject** the document immediately
2. Provide reason: "Document requires further verification"
3. **Escalate** to supervisor or compliance team
4. Document your concerns in internal notes

### Time Management

**Average Review Times**:
- Simple approval (clear ID): 30 seconds
- Detailed review (business docs): 2-3 minutes
- Rejection with reason: 1-2 minutes

**Recommended Session Length**:
- Short session: 15-20 minutes (5-10 documents)
- Long session: 45-60 minutes (20-30 documents)
- Take breaks between sessions to maintain focus

## Keyboard Shortcuts

Enhance your workflow with keyboard navigation:

- **Tab**: Navigate between elements
- **Enter**: Activate focused button
- **Escape**: Close dialog/modal
- **Space**: Toggle focused checkbox
- **Arrow keys**: Navigate dropdowns

## Troubleshooting

### Common Issues

**Problem**: Document preview not loading
- **Solution**: Check internet connection, refresh page
- **Alternative**: Use download button to view offline

**Problem**: Approve/Reject button is disabled
- **Solution**: Ensure you've entered a rejection reason (if rejecting)
- **Solution**: Check if another action is in progress (wait for loading)

**Problem**: Document doesn't match expected type
- **Solution**: Reject with reason: "Wrong document type. Please upload [correct type]"

**Problem**: Can't read document text
- **Solution**: Reject with reason: "Image is too blurry. Please upload a clearer photo"

**Problem**: Organizer uploaded multiple documents in one file
- **Solution**: If all are relevant, approve. If mixed, reject with instructions.

## Reporting & Analytics

### Track Your Performance

While not directly displayed on this page, admins can track:
- Number of documents reviewed per day
- Approval vs. rejection rate
- Average review time
- Escalations to compliance

(Access these reports via Analytics → Admin Activity)

### Audit Trail

Every action is logged:
- Document approvals (who, when)
- Document rejections (who, when, reason)
- Preview actions
- Search queries

(Access audit logs via System → Audit Logs)

## Escalation Process

### When to Escalate

Escalate to a supervisor or compliance team if:
- You suspect fraudulent documents
- You're unsure about document authenticity
- Organizer is arguing with your decision
- Document type is unusual or not in standard list
- Legal questions arise

### How to Escalate

1. **Do not approve or reject** the document
2. Contact supervisor via internal messaging
3. Document your concerns
4. Wait for guidance before proceeding
5. Follow supervisor's decision

## FAQs

**Q: How many documents must an organizer submit?**
A: Typically 3-5 documents:
- National ID (required)
- Tax Certificate (required)
- Business License (for businesses)
- Bank Statement (for payouts)
- Proof of Address (optional)

**Q: What if a document is partially correct?**
A: Reject with specific feedback. Example: "The Business License is valid, but page 2 is missing. Please upload complete document."

**Q: Can I approve a document that expires soon?**
A: Yes, if it's currently valid. Note: System may require renewal notification.

**Q: What if organizer uploads the wrong file format?**
A: Reject with reason: "Please upload as PDF or JPEG image."

**Q: How long should I keep checking this page?**
A: Check at least twice daily during business hours. Enable notifications if available.

**Q: Can I undo an approval or rejection?**
A: No, decisions are final. If you made an error, escalate to supervisor.

**Q: What happens if I don't review a document?**
A: Organizer's application remains in PENDING status. They cannot publish events or receive payouts until documents are verified.

## Getting Help

### Support Resources

- **Technical Issues**: Contact IT Support at support@pml.com
- **Policy Questions**: Contact Compliance Team at compliance@pml.com
- **Escalations**: Contact your supervisor directly
- **Training**: Review this guide, attend quarterly training sessions

### Training Materials

- Video walkthrough: [Link to training video]
- Policy handbook: [Link to compliance policies]
- FAQ database: [Link to knowledge base]
- Fraud detection guide: [Link to fraud prevention docs]

## Summary

The Document Verification page is your primary tool for ensuring organizer compliance. By thoroughly reviewing documents and making fair, consistent decisions, you help maintain the integrity of the ticketing platform while enabling legitimate organizers to succeed.

**Remember**:
- Quality over speed
- Clarity in rejections
- Consistency in standards
- Escalate when uncertain

Your careful review protects both the platform and its users. Thank you for your diligence!
