# Cross-Border Bill Payment Platform
### Canada to Japan | FinTech Project

## 1. Project Overview

This project proposes a cross-border bill payment platform that enables Canadian residents to pay utility bills and other eligible expenses in Japan through a single application.

The platform is inspired by MyBeacon's India Bill Pay service, which enables customers in Canada to pay bills in India through partnerships with YES BANK and Bharat Connect (BBPS).

The proposed solution adapts this concept to Japan by combining:

- **Web Application** — Customer interface for bill registration, payment requests, and transaction tracking.
- **Wise** — Cross-border money transfer and currency conversion from CAD to JPY.
- **Japanese Payment Agency** — A proposed Japan-based legal entity responsible for receiving funds and processing bill payments on behalf of customers.
- **Pay-easy** — Japan's payment infrastructure for eligible taxes, utilities, and other bills.

**Project Status:** Concept / Proof of Concept (PoC)

This repository represents a proposed architecture. It does not imply that commercial partnerships, regulatory approvals, or production payment integrations have been established.

---

## 2. Background

### MyBeacon's India Bill Pay Model

MyBeacon provides a cross-border bill payment service that allows customers in Canada to pay bills in India.

The service relies on several participants:

| Participant | Responsibility |
|---|---|
| Customer | Initiates bill payments from Canada |
| MyBeacon | Provides the customer-facing application and coordinates payment services |
| YES BANK | Supports Indian-side banking, settlement, and payment infrastructure |
| Bharat Connect (BBPS) | Connects bill payment services with participating Indian billers |
| Indian Billers | Receive payments for utilities, insurance, education, loans, and other services |

This model demonstrates how a FinTech application can provide access to a large biller network without integrating directly with every individual service provider.

### Opportunity: Canada to Japan

Canadian residents who maintain financial obligations in Japan may need to pay:

- Electricity bills
- Water and gas bills
- Telecommunications and internet bills
- National and local taxes
- Insurance premiums
- Other eligible invoices

These payments may require access to Japanese banking services or specific domestic payment methods.

The proposed platform aims to simplify this process through a centralized payment interface.

---

## 3. Proposed Solution

The proposed solution introduces a Japanese payment agency that receives JPY through Wise and processes eligible payments using Pay-easy.

### High-Level Architecture

```text
                   CANADA
------------------------------------------------

                  Customer
                     |
                     | Bill information
                     | Payment request
                     v
               Web Application
                     |
                     | CAD payment
                     v
                    Wise
                     |
                     | CAD -> JPY
                     | International transfer
                     v

                    JAPAN
------------------------------------------------

            Japanese Payment Agency
                     |
                     | JPY received
                     | Payment reconciliation
                     | Bill verification
                     v
           Japanese Corporate Bank
                     |
                     | Corporate Internet Banking
                     v
                  Pay-easy
                     |
                     | Bill payment
                     v
               Japanese Billers
                     |
             +-------+--------+
             |       |        |
          Utilities Taxes   Other Bills
```

**Important:** Wise integration, payment agency operations, and Pay-easy payment execution are proposed components. Direct API access to Pay-easy is not assumed.

---

## 4. Responsibilities

| Component | Responsibilities |
|---|---|
| Customer | Registers bills, submits payment requests, and reviews transaction status |
| Web Application | Manages customers, bill information, payment requests, and transaction history |
| Wise | Facilitates CAD-to-JPY conversion and international transfers, subject to account and integration eligibility |
| Japanese Payment Agency | Receives funds, reconciles transactions, verifies payment requests, and coordinates domestic bill payments |
| Japanese Corporate Bank | Provides the corporate bank account and eligible internet banking payment services |
| Pay-easy | Processes supported bill payments through participating financial institutions and billers |
| Japanese Billers | Receive payments and apply them to the corresponding customer obligations |

---

## 5. Payment Workflow

### Step 1 — Bill Registration

The customer registers a Japanese bill through the application.

Depending on the bill type, the required information may include:

- Biller or service provider
- Customer or contract number
- Payment reference number
- Payment amount
- Due date
- Pay-easy payment identifiers, where applicable

### Step 2 — Payment Request

The customer selects a registered bill and submits a payment request.

The application displays the estimated payment amount in CAD, including applicable fees and exchange-rate information.

### Step 3 — International Transfer

The customer transfers funds using Wise.

Wise facilitates the currency conversion and international transfer from CAD to JPY, subject to the supported payment flow and required approvals.

### Step 4 — Payment Agency Processing

The Japanese payment agency receives the transferred funds into its designated corporate bank account.

The agency then:

1. Identifies and reconciles the incoming transfer.
2. Matches the funds to the customer's payment request.
3. Verifies the payment information.
4. Confirms that sufficient funds are available.
5. Prepares the domestic bill payment.

### Step 5 — Pay-easy Payment

An authorized operator uses the Japanese corporate bank's supported Pay-easy service to process the payment.

For the initial PoC, this step is assumed to be manual.

The platform does not assume that Pay-easy provides a publicly available API for third-party payment execution.

### Step 6 — Payment Confirmation

After the payment result is verified, the agency updates the transaction record.

The customer can view the payment status and available confirmation details through the application.

---

## 6. Core Features

### Customer Features

- Customer registration and authentication
- Japanese bill registration and management
- Payment request submission
- Estimated CAD-to-JPY conversion display
- Payment status tracking
- Transaction history
- Payment confirmation notifications

### Payment Agency Features

- Administrative dashboard
- Customer and bill management
- Incoming transfer reconciliation
- Payment request validation
- Pay-easy processing queue
- Manual payment confirmation
- Transaction auditing and reporting

---

## 7. Payment Status Management

The proposed system tracks payment requests using the following statuses:

| Status | Description |
|---|---|
| CREATED | Payment request has been created |
| PENDING_TRANSFER | Waiting for customer funds |
| FUNDS_RECEIVED | Incoming funds have been identified and reconciled |
| READY_FOR_PAYMENT | Payment request has passed validation |
| PROCESSING | Domestic bill payment is being processed |
| COMPLETED | Successful payment has been confirmed |
| FAILED | Payment processing failed |
| CANCELLED | Payment request was cancelled |

A transfer being received does not automatically mean the Japanese bill has been paid.

---

## 8. Proposed Development Roadmap

### Phase 1 — Proof of Concept

**Objective:** Validate the application workflow without processing real customer funds.

- Implement customer authentication
- Develop bill registration functionality
- Create payment request management
- Simulate CAD-to-JPY transfers
- Implement an administrative payment dashboard
- Simulate manual Pay-easy payment confirmation
- Store payment history and audit logs

### Phase 2 — Payment Integration

**Objective:** Investigate and implement supported payment integrations.

- Evaluate Wise Business and API eligibility
- Design transfer reconciliation
- Introduce payment notifications
- Improve bill validation
- Evaluate corporate internet banking operations
- Complete relevant legal and compliance assessments

### Phase 3 — Operational Automation

**Objective:** Reduce manual payment processing.

- Investigate authorized banking integration options
- Automate supported reconciliation processes
- Introduce payment scheduling
- Improve transaction monitoring
- Expand supported bill categories

Any payment execution automation would depend on the capabilities and contractual permissions of the participating financial institution.

---

## 9. Security and Compliance Considerations

Because the proposed platform involves cross-border transfers and third-party bill payments, security and regulatory compliance are essential.

Key areas include:

- Customer identity verification (KYC)
- Anti-money laundering (AML)
- FINTRAC and Canadian money services business requirements
- Japanese Payment Services Act requirements
- Cross-border collection and payment agency regulations
- Customer data protection
- Secure API authentication and authorization
- Audit logging and transaction traceability
- Prevention of duplicate payments
- Payment reconciliation and exception handling

Establishing a Japanese corporation or using a corporate bank account does not automatically authorize the business to handle third-party funds.

Production deployment would require confirmation of the applicable regulatory requirements and necessary commercial agreements.

---

## 10. Key Technical Challenges

### Cross-Border Transfer Integration

Determining whether the proposed Wise integration supports the intended customer payment and Japanese corporate account settlement model.

### Domestic Bill Payment Execution

Pay-easy supports eligible payments through participating Japanese financial institutions. However, corporate internet banking access does not necessarily provide a programmable payment API.

### Payment Reconciliation

Each incoming transfer must be reliably matched with the correct customer and bill payment request.

### Duplicate Payment Prevention

The platform must prevent the same payment request from being executed multiple times.

### Transaction Consistency

The system must distinguish between international transfer completion and domestic bill payment completion.

### Regulatory Compliance

The proposed business model must be evaluated under both Canadian and Japanese financial regulations before processing real customer funds.

---

## 11. Project Goal

The long-term goal is to create a secure, scalable cross-border bill payment platform that simplifies the management of financial obligations in Japan for customers living in Canada.

The initial focus is on validating the customer experience, transaction lifecycle, and payment agency operations through a Proof of Concept.

Future development will depend on the availability of suitable banking integrations, payment service partnerships, and regulatory approvals.

---

## 12. Disclaimer

This project is an independent FinTech concept and software development initiative.

It is not affiliated with, endorsed by, or officially partnered with MyBeacon, Wise, Pay-easy, YES BANK, Bharat Connect, or NTT DATA.

All external integrations and business relationships described in this document are conceptual unless explicitly identified as implemented and authorized.

No real customer funds are processed as part of the initial Proof of Concept.
