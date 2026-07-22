package com.invsys.integration.easypost;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;

/**
 * Live EasyPost rate shopping + label purchase. Active only on {@code prod}.
 * Requires {@code EASYPOST_API_KEY} and ship-from defaults (or parcel addresses).
 */
@Component
@Profile("prod")
public class LiveEasyPostGateway implements EasyPostGateway, EasyPostClient {

    private static final Logger log = LoggerFactory.getLogger(LiveEasyPostGateway.class);

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final EasyPostProperties properties;

    public LiveEasyPostGateway(ObjectMapper objectMapper, EasyPostProperties properties) {
        String key = properties.getApiKey();
        if (key == null || key.isBlank() || "easypost_mock_key".equals(key)) {
            throw new IllegalStateException(
                    "EASYPOST_API_KEY (invsys.easypost.api-key) must be configured for production profile");
        }
        this.objectMapper = objectMapper;
        this.apiKey = key;
        this.properties = properties;
    }

    @Override
    public LabelResult purchaseLabel(String carrier, BigDecimal weightLb, String reference) {
        return shopAndBuyCheapest(
                new ParcelSpec(new BigDecimal("10"), new BigDecimal("8"), new BigDecimal("6"), weightLb),
                reference,
                LabelOptions.pdfDefault())
                .purchased();
    }

    @Override
    public List<RateQuote> shopRates(ParcelSpec parcel, String reference, LabelOptions labelOptions) {
        RatedShipment rated = createRatedShipment(parcel, reference, labelOptions, false);
        return rated.rates();
    }

    @Override
    public ShopResult shopAndBuyCheapest(ParcelSpec parcel, String reference, LabelOptions labelOptions) {
        RatedShipment rated = createRatedShipment(parcel, reference, labelOptions, false);
        RateQuote cheapest = cheapest(rated.rates());
        LabelResult purchased = buyShipment(rated.shipmentId(), cheapest, labelOptions);
        return new ShopResult(List.copyOf(rated.rates()), purchased);
    }

    @Override
    public LabelResult purchaseReturnLabel(ParcelSpec parcel, String reference) {
        ParcelSpec returnParcel = parcel != null ? parcel.asReturn() : null;
        RatedShipment rated = createRatedShipment(returnParcel, reference, LabelOptions.pdfDefault(), true);
        RateQuote cheapest = cheapest(rated.rates());
        return buyShipment(rated.shipmentId(), cheapest, LabelOptions.pdfDefault());
    }

    private RatedShipment createRatedShipment(
            ParcelSpec parcel, String reference, LabelOptions labelOptions, boolean forceReturn) {
        LabelOptions opts = labelOptions != null ? labelOptions : LabelOptions.pdfDefault();
        try {
            ObjectNode root = objectMapper.createObjectNode();
            ObjectNode shipment = root.putObject("shipment");
            shipment.put("reference", reference != null ? reference : "invsys");
            if (forceReturn || (parcel != null && parcel.isReturn())) {
                shipment.put("is_return", true);
            }

            putAddress(shipment, "to_address", resolveTo(parcel));
            putAddress(shipment, "from_address", resolveFrom(parcel));

            String oz = (parcel == null || parcel.weightLb() == null || parcel.weightLb().signum() <= 0
                    ? BigDecimal.ONE
                    : parcel.weightLb())
                    .multiply(new BigDecimal("16"))
                    .setScale(1, RoundingMode.HALF_UP)
                    .toPlainString();
            ObjectNode parcelNode = shipment.putObject("parcel");
            parcelNode.put("weight", oz);
            parcelNode.put("length", nullTo(parcel != null ? parcel.lengthIn() : null, "10"));
            parcelNode.put("width", nullTo(parcel != null ? parcel.widthIn() : null, "8"));
            parcelNode.put("height", nullTo(parcel != null ? parcel.heightIn() : null, "6"));
            ObjectNode options = shipment.putObject("options");
            options.put("label_format", opts.normalizedFormat());
            options.put("label_size", opts.normalizedSize());

            JsonNode created = postJson("https://api.easypost.com/v2/shipments", root);
            String shipmentId = created.path("id").asText(null);
            if (shipmentId == null || shipmentId.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "EasyPost shipment missing id");
            }

            List<RateQuote> rates = new ArrayList<>();
            for (JsonNode rateNode : created.path("rates")) {
                String rateText = rateNode.path("rate").asText(null);
                if (rateText == null) {
                    continue;
                }
                rates.add(new RateQuote(
                        rateNode.path("carrier").asText("UNKNOWN"),
                        rateNode.path("service").asText(""),
                        rateNode.path("id").asText(""),
                        new BigDecimal(rateText),
                        rateNode.path("currency").asText("USD")));
            }
            if (rates.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "EasyPost returned no rates");
            }
            return new RatedShipment(shipmentId, rates);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "EasyPost API call failed", ex);
        }
    }

    private LabelResult buyShipment(String shipmentId, RateQuote cheapest, LabelOptions labelOptions) {
        LabelOptions opts = labelOptions != null ? labelOptions : LabelOptions.pdfDefault();
        try {
            ObjectNode buyBody = objectMapper.createObjectNode();
            buyBody.putObject("rate").put("id", cheapest.rateId());
            JsonNode bought = postJson(
                    "https://api.easypost.com/v2/shipments/" + shipmentId + "/buy", buyBody);

            String tracking = bought.path("tracking_code").asText("EP-" + System.currentTimeMillis());
            JsonNode postageLabel = bought.path("postage_label");
            String labelUrl = postageLabel.path("label_url").asText("easypost://" + tracking);
            String fileType = postageLabel.path("label_file_type").asText(opts.normalizedFormat());
            if (fileType == null || fileType.isBlank()) {
                fileType = opts.normalizedFormat();
            }
            BigDecimal postage = cheapest.rate();
            if (bought.path("selected_rate").path("rate").isValueNode()) {
                postage = new BigDecimal(bought.path("selected_rate").path("rate").asText(postage.toPlainString()));
            }
            return new LabelResult(
                    labelUrl, tracking, postage, cheapest.carrier(), cheapest.service(),
                    fileType.toUpperCase());
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "EasyPost label purchase failed", ex);
        }
    }

    private AddressSpec resolveTo(ParcelSpec parcel) {
        if (parcel != null && parcel.toAddress() != null) {
            return parcel.toAddress();
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Ship-to address required for EasyPost (customer shipping_address incomplete)");
    }

    private AddressSpec resolveFrom(ParcelSpec parcel) {
        if (parcel != null && parcel.fromAddress() != null) {
            return parcel.fromAddress();
        }
        AddressSpec configured = properties.defaultFromAddress();
        if (configured != null) {
            return configured;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Ship-from address required — set invsys.easypost.default-from.* env vars");
    }

    private void putAddress(ObjectNode shipment, String field, AddressSpec address) {
        ObjectNode node = shipment.putObject(field);
        putIfPresent(node, "name", address.name());
        putIfPresent(node, "company", address.company());
        putIfPresent(node, "street1", address.street1());
        putIfPresent(node, "street2", address.street2());
        putIfPresent(node, "city", address.city());
        putIfPresent(node, "state", address.state());
        putIfPresent(node, "zip", address.zip());
        putIfPresent(node, "country", address.country());
        putIfPresent(node, "phone", address.phone());
        putIfPresent(node, "email", address.email());
    }

    private static void putIfPresent(ObjectNode node, String field, String value) {
        if (value != null && !value.isBlank()) {
            node.put(field, value);
        }
    }

    private JsonNode postJson(String url, ObjectNode body) throws Exception {
        String auth = Base64.getEncoder().encodeToString((apiKey + ":").getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Basic " + auth)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            // Never echo EasyPost body to API clients (may contain PII / account hints)
            log.warn("EasyPost HTTP {} for {}: {}", response.statusCode(), url, truncate(response.body()));
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "EasyPost API error HTTP " + response.statusCode());
        }
        return objectMapper.readTree(response.body());
    }

    private static String truncate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= 500 ? body : body.substring(0, 500) + "…";
    }

    private static RateQuote cheapest(List<RateQuote> rates) {
        return rates.stream().min(Comparator.comparing(RateQuote::rate)).orElseThrow();
    }

    private static String nullTo(BigDecimal value, String fallback) {
        if (value == null || value.signum() <= 0) {
            return fallback;
        }
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private record RatedShipment(String shipmentId, List<RateQuote> rates) {
    }
}
