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

@JsonIgnoreProperties(ignoreUnknown = true)
public record KmaMidWeatherApiResponse(Response response) {

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
            Integer rnSt3Am, Integer rnSt3Pm, Integer rnSt4Am, Integer rnSt4Pm, Integer rnSt5Am, Integer rnSt5Pm, Integer rnSt6Am, Integer rnSt6Pm, Integer rnSt7Am, Integer rnSt7Pm,
            Integer rnSt8, Integer rnSt9, Integer rnSt10,
            String wf3Am, String wf3Pm, String wf4Am, String wf4Pm, String wf5Am, String wf5Pm, String wf6Am, String wf6Pm, String wf7Am, String wf7Pm,
            String wf8, String wf9, String wf10
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
