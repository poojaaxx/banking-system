package com.bankingdemo.ai.categorization;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Deterministic keyword categorizer -- always available, no network call.
 * This is the default suggestion shown for every uncategorized transaction;
 * the AI suggestion (see AiCategorizer) is an optional, on-demand upgrade the
 * customer can ask for instead.
 */
@Component
public class RuleBasedCategorizer {

    private static final Map<String, List<String>> KEYWORDS_BY_CODE = new LinkedHashMap<>();

    static {
        KEYWORDS_BY_CODE.put("GROCERIES", List.of("grocery", "groceries", "supermarket", "kirana", "bigbasket", "dmart"));
        KEYWORDS_BY_CODE.put("TRANSPORT", List.of("uber", "ola", "taxi", "metro", "fuel", "petrol", "diesel", "parking", "irctc", "train", "flight", "airlines"));
        KEYWORDS_BY_CODE.put("UTILITIES", List.of("electricity", "water bill", "broadband", "wifi bill", "internet bill", "gas bill", "power bill"));
        KEYWORDS_BY_CODE.put("ENTERTAINMENT", List.of("netflix", "prime video", "hotstar", "spotify", "movie", "cinema", "bookmyshow", "concert"));
        KEYWORDS_BY_CODE.put("DINING", List.of("restaurant", "cafe", "swiggy", "zomato", "food", "dining", "pizza", "coffee", "bakery"));
        KEYWORDS_BY_CODE.put("SHOPPING", List.of("amazon", "flipkart", "myntra", "mall", "shopping", "store", "ajio"));
        KEYWORDS_BY_CODE.put("BILLS", List.of("recharge", "bill payment", "dth", "mobile bill", "postpaid", "prepaid", "broadband bill"));
        KEYWORDS_BY_CODE.put("SAVINGS", List.of("savings goal", "goal contribution", "fixed deposit"));
    }

    /** Empty when no keyword confidently matches -- callers should not force a guess (e.g. "Other"). */
    public Optional<RuleMatch> suggestCode(String description) {
        if (description == null || description.isBlank()) {
            return Optional.empty();
        }
        String lower = description.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, List<String>> entry : KEYWORDS_BY_CODE.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (lower.contains(keyword)) {
                    return Optional.of(new RuleMatch(entry.getKey(), 0.7));
                }
            }
        }
        return Optional.empty();
    }

    public record RuleMatch(String categoryCode, double confidence) {
    }
}
