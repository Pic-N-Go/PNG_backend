package com.project.picngo.external.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * 기상청 중기기온예보(getMidTa) 응답.
 * 중기육상예보(getMidLandFcst)는 하늘상태만 주고 기온이 없어, 기온은 이 API로 별도 조회한다.
 * 제공값은 일자별 최저(taMin)/최고(taMax) 뿐이며 시간대별 값은 없다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KmaMidTaApiResponse(Response response) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(Header header, Body body) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Header(String resultCode, String resultMsg) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(
            String dataType,
            @JsonDeserialize(using = ItemsDeserializer.class) Items items,
            int pageNo,
            int numOfRows,
            int totalCount
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Items(List<Item> item) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            String regId,
            Integer taMin3, Integer taMax3, Integer taMin4, Integer taMax4, Integer taMin5, Integer taMax5,
            Integer taMin6, Integer taMax6, Integer taMin7, Integer taMax7,
            Integer taMin8, Integer taMax8, Integer taMin9, Integer taMax9, Integer taMin10, Integer taMax10
    ) {}

    /**
     * 기상청 OpenAPI는 결과가 없을 때 items 필드를 빈 객체({})가 아닌 빈 문자열("")로
     * 반환하는 경우가 있어 역직렬화 실패를 방지하는 커스텀 역직렬화기.
     */
    public static class ItemsDeserializer extends JsonDeserializer<Items> {
        @Override
        public Items deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonToken token = p.currentToken();
            if (token == JsonToken.VALUE_STRING) {
                return new Items(Collections.emptyList());
            }
            if (token == JsonToken.START_OBJECT) {
                JsonNode node = p.getCodec().readTree(p);
                JsonNode itemNode = node.get("item");
                if (itemNode == null || itemNode.isNull()) {
                    return new Items(Collections.emptyList());
                }
                ObjectMapper mapper = (ObjectMapper) p.getCodec();
                if (itemNode.isArray()) {
                    List<Item> list = mapper.readerForListOf(Item.class).readValue(itemNode);
                    return new Items(list);
                }
                if (itemNode.isObject()) {
                    Item single = mapper.treeToValue(itemNode, Item.class);
                    return new Items(List.of(single));
                }
            }
            return new Items(Collections.emptyList());
        }
    }
}
