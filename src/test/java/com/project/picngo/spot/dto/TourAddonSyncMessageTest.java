package com.project.picngo.spot.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TourAddonSyncMessageTest {

    @Test
    void sampleSyncLimitsAddonDetailsToRequestedSampleCount() {
        TourApiSyncMessage source = TourApiSyncMessage.ofSample(20, 1L);

        PetTourSyncMessage petMessage = PetTourSyncMessage.afterSpotSync(source);
        AccessibilityTourSyncMessage accessibilityMessage =
                AccessibilityTourSyncMessage.afterSpotSync(source);

        assertThat(petMessage.maxDetailsPerType()).isEqualTo(20);
        assertThat(accessibilityMessage.maxDetailsPerType()).isEqualTo(20);
    }

    @Test
    void areaSyncPropagatesAreaCodesAndUsesDefaultLimit() {
        TourApiSyncMessage source = TourApiSyncMessage.ofArea(1, null, null, 1L);

        PetTourSyncMessage petMessage = PetTourSyncMessage.afterSpotSync(source);
        AccessibilityTourSyncMessage accessibilityMessage =
                AccessibilityTourSyncMessage.afterSpotSync(source);

        assertThat(petMessage.legalRegionCode()).isEqualTo(11);
        assertThat(accessibilityMessage.areaCode()).isEqualTo(1);
        assertThat(petMessage.maxDetailsPerType()).isEqualTo(1_000);
        assertThat(accessibilityMessage.maxDetailsPerType()).isEqualTo(1_000);
    }
}
