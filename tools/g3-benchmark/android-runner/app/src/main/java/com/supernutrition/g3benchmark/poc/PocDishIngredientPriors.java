package com.supernutrition.g3benchmark.poc;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Research-only dish-to-ingredient plausibility priors for debug ranking. */
public final class PocDishIngredientPriors {
    private static final Map<String, List<String>> PRIORS = createPriors();

    private PocDishIngredientPriors() {
    }

    public static List<String> forDish(String dishLabel) {
        if (dishLabel == null) {
            return Collections.emptyList();
        }
        List<String> labels = PRIORS.get(dishLabel.trim().toLowerCase());
        return labels == null ? Collections.<String>emptyList() : labels;
    }

    private static Map<String, List<String>> createPriors() {
        Map<String, List<String>> priors = new LinkedHashMap<>();
        priors.put("greek_salad", list(
                "cucumber", "tomato", "red_onion", "bell_pepper", "feta_cheese", "olives", "olive_oil"));
        priors.put("caesar_salad", list(
                "romaine_lettuce", "chicken", "parmesan", "croutons", "caesar_dressing"));
        priors.put("garden_salad", list(
                "lettuce", "tomato", "cucumber", "carrot", "onion", "bell_pepper"));
        priors.put("chopped_salad", list(
                "lettuce", "tomato", "cucumber", "carrot", "corn", "onion"));
        priors.put("hot_and_sour_soup", list(
                "tofu", "mushroom", "bamboo_shoots", "pork", "egg", "vinegar"));
        priors.put("miso_soup", list(
                "miso", "tofu", "seaweed", "green_onion", "mushroom"));
        priors.put("ramen", list(
                "noodles", "pork", "egg", "seaweed", "green_onion", "broth"));
        priors.put("pizza", list(
                "dough", "tomato_sauce", "mozzarella", "cheese", "pepperoni", "mushroom", "onion"));
        priors.put("pork_chop", list(
                "pork_chop", "pork", "carrot", "potato", "green_beans", "apple_sauce"));
        priors.put("steak", list(
                "beef", "potato", "green_beans", "salad", "sauce"));
        priors.put("grilled_salmon", list(
                "salmon", "lemon", "rice", "broccoli", "asparagus"));
        priors.put("sushi", list(
                "rice", "salmon", "tuna", "avocado", "cucumber", "seaweed"));
        priors.put("fried_rice", list(
                "rice", "egg", "peas", "carrot", "green_onion"));
        priors.put("bibimbap", list(
                "rice", "beef", "egg", "carrot", "spinach", "mushroom"));
        priors.put("chicken_curry", list(
                "chicken", "rice", "onion", "tomato", "coconut_milk"));
        priors.put("hamburger", list(
                "beef", "bun", "lettuce", "tomato", "onion", "pickle", "cheese"));
        priors.put("lasagna", list(
                "pasta", "tomato_sauce", "beef", "cheese", "ricotta"));
        priors.put("spaghetti_bolognese", list(
                "pasta", "beef", "tomato_sauce", "parmesan"));
        priors.put("waffles", list(
                "flour", "egg", "milk", "butter", "syrup", "berries"));
        return Collections.unmodifiableMap(priors);
    }

    private static List<String> list(String... labels) {
        return Collections.unmodifiableList(Arrays.asList(labels));
    }
}
