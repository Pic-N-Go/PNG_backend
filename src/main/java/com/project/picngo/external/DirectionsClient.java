package com.project.picngo.external;

import com.project.picngo.external.dto.DirectionsResponse;

// 길찾기 API를 호출하는 클라이언트 인터페이스
public interface DirectionsClient {
    DirectionsResponse getTravelInfo(Double startLat, Double startLng, Double goalLat, Double goalLng);
    
    Integer getTravelTimeMinutes(Double startLat, Double startLng, Double goalLat, Double goalLng);
}
