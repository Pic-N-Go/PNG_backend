package com.project.picngo.external.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TourApiIntroResponse(Response response) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(Body body) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(Items items) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Items(List<IntroItem> item) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IntroItem(
            String contentid,
            // [타입 12: 관광지]
            String parking,
            String usetime,
            String restdate,
            String infocenter,
            String chkbabycarriage,
            String chkpet,
            String chkcreditcard,
            String chkhandicap,

            // [타입 14: 문화시설]
            String parkingculture,
            String parkingfee,
            String usetimeculture,
            String usefee,
            String restdateculture,
            String infocenterculture,
            String spendtime,
            String scale,
            String chkbabycarriageculture,
            String chkpetculture,
            String chkcreditcardculture,

            // [타입 15: 축제/공연/행사]
            String eventstartdate,
            String eventenddate,
            String eventplace,
            String playtime,
            String usetimefestival,
            String bookingplace,
            String discountinfofestival,
            String spendtimefestival,

            // [타입 39: 음식점/카페]
            String firstmenu,
            String treatmenu,
            String opentimefood,
            String restdatefood,
            String parkingfood,
            String packing,
            String infocenterfood,
            String chkcreditcardfood,
            String reservationfood,

            // [타입 32: 숙박]
            String checkintime,
            String checkouttime,
            String chkcooking,
            String parkinglodging,
            String infocenterlodging,
            String reservationurl,
            String subfacility,

            // [타입 28: 레포츠]
            String parkingleports,
            String usetimeleports,
            String restdateleports,
            String infocenterleports,
            String usefeeleports
    ) {
        /**
         * 관광타입에 관계없이 유효한 이용시간/영업시간을 반환한다.
         */
        public String getEffectiveUsetime() {
            if (usetime != null && !usetime.isBlank()) return usetime.trim();
            if (usetimeculture != null && !usetimeculture.isBlank()) return usetimeculture.trim();
            if (opentimefood != null && !opentimefood.isBlank()) return opentimefood.trim();
            if (usetimeleports != null && !usetimeleports.isBlank()) return usetimeleports.trim();
            if (playtime != null && !playtime.isBlank()) return playtime.trim();
            return null;
        }

        /**
         * 관광타입에 관계없이 유효한 주차장 정보를 반환한다.
         */
        public String getEffectiveParking() {
            if (parking != null && !parking.isBlank()) return parking.trim();
            if (parkingculture != null && !parkingculture.isBlank()) return parkingculture.trim();
            if (parkingfood != null && !parkingfood.isBlank()) return parkingfood.trim();
            if (parkinglodging != null && !parkinglodging.isBlank()) return parkinglodging.trim();
            if (parkingleports != null && !parkingleports.isBlank()) return parkingleports.trim();
            return null;
        }

        /**
         * 관광타입에 관계없이 유효한 휴무일/쉬는날 정보를 반환한다.
         */
        public String getEffectiveRestdate() {
            if (restdate != null && !restdate.isBlank()) return restdate.trim();
            if (restdateculture != null && !restdateculture.isBlank()) return restdateculture.trim();
            if (restdatefood != null && !restdatefood.isBlank()) return restdatefood.trim();
            if (restdateleports != null && !restdateleports.isBlank()) return restdateleports.trim();
            return null;
        }

        /**
         * 관광타입에 관계없이 유효한 문의전화/안내처를 반환한다.
         */
        public String getEffectiveInfocenter() {
            if (infocenter != null && !infocenter.isBlank()) return infocenter.trim();
            if (infocenterculture != null && !infocenterculture.isBlank()) return infocenterculture.trim();
            if (infocenterfood != null && !infocenterfood.isBlank()) return infocenterfood.trim();
            if (infocenterlodging != null && !infocenterlodging.isBlank()) return infocenterlodging.trim();
            if (infocenterleports != null && !infocenterleports.isBlank()) return infocenterleports.trim();
            return null;
        }

        /**
         * 유모차 대여/접근성 정보를 반환한다.
         */
        public String getEffectiveStrollerAccess() {
            if (chkbabycarriage != null && !chkbabycarriage.isBlank()) return chkbabycarriage.trim();
            if (chkbabycarriageculture != null && !chkbabycarriageculture.isBlank()) return chkbabycarriageculture.trim();
            return null;
        }

        /**
         * 반려동물 동반 가능 여부 정보를 반환한다.
         */
        public String getEffectivePetFriendly() {
            if (chkpet != null && !chkpet.isBlank()) return chkpet.trim();
            if (chkpetculture != null && !chkpetculture.isBlank()) return chkpetculture.trim();
            return null;
        }

        /**
         * 휠체어/장애인 편의시설 정보를 반환한다.
         */
        public String getEffectiveWheelchairAccess() {
            if (chkhandicap != null && !chkhandicap.isBlank()) return chkhandicap.trim();
            return null;
        }
    }
}
