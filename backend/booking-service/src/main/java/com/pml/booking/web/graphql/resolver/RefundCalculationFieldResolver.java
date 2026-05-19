package com.pml.booking.web.graphql.resolver;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsData;
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment;
import com.pml.booking.web.graphql.dto.RefundCalculation;
import lombok.extern.slf4j.Slf4j;

/**
 * Field Resolver for RefundCalculation type.
 *
 * Resolves fields with name mismatches between DTO and schema:
 * - policyApplied: maps to policyDetails
 * - ineligibleReason: maps to ineligibilityReason
 */
@Slf4j
@DgsComponent
public class RefundCalculationFieldResolver {

    /**
     * Resolve RefundCalculation.policyApplied - maps to policyDetails.
     */
    @DgsData(parentType = "RefundCalculation", field = "policyApplied")
    public String policyApplied(DgsDataFetchingEnvironment dfe) {
        RefundCalculation calc = dfe.getSource();
        String policyDetails = calc.getPolicyDetails();

        if (policyDetails != null) {
            return policyDetails;
        }

        // Generate policy description based on refund percentage
        float percentage = calc.getRefundPercentage();
        if (percentage >= 100) {
            return "FULL_REFUND";
        } else if (percentage >= 75) {
            return "75_PERCENT_REFUND";
        } else if (percentage >= 50) {
            return "50_PERCENT_REFUND";
        } else if (percentage >= 25) {
            return "25_PERCENT_REFUND";
        } else if (percentage > 0) {
            return "PARTIAL_REFUND";
        } else {
            return "NO_REFUND";
        }
    }

    /**
     * Resolve RefundCalculation.ineligibleReason - maps to ineligibilityReason.
     */
    @DgsData(parentType = "RefundCalculation", field = "ineligibleReason")
    public String ineligibleReason(DgsDataFetchingEnvironment dfe) {
        RefundCalculation calc = dfe.getSource();
        return calc.getIneligibilityReason();
    }
}
