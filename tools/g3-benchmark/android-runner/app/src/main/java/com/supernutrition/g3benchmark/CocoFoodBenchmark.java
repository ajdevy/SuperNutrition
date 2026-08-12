package com.supernutrition.g3benchmark;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Shared fixture and COCO vocabulary contract for detector-only research activities. */
final class CocoFoodBenchmark {
    static final String[] FIXTURES = {
            "steak-side-plate.jpg", "fish-side-plate.jpg", "chopped-salad.jpg", "buddha-bowl.jpg", "salmon-grain-plate.jpg"
    };
    static final String[][] EXPECTED_VISIBLE_COMPONENTS = {
            {"beef_steak", "quinoa", "carrot", "cucumber", "green_bean", "creamy_sauce"},
            {"fish", "broccoli", "carrot", "onion", "celery"},
            {"carrot", "tomato", "cucumber", "corn", "chili_pepper", "cilantro"},
            {"avocado", "white_rice", "tempeh", "edamame", "radish", "lettuce", "bean_sprout", "bell_pepper", "sesame", "green_onion"},
            {"fish", "white_rice", "beans", "leafy_greens", "tomato_sauce", "carrot"}
    };
    static final String[] COCO_NAMES = {
            "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat", "traffic_light",
            "fire_hydrant", "stop_sign", "parking_meter", "bench", "bird", "cat", "dog", "horse", "sheep", "cow",
            "elephant", "bear", "zebra", "giraffe", "backpack", "umbrella", "handbag", "tie", "suitcase", "frisbee",
            "skis", "snowboard", "sports_ball", "kite", "baseball_bat", "baseball_glove", "skateboard", "surfboard", "tennis_racket", "bottle",
            "wine_glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple", "sandwich", "orange",
            "broccoli", "carrot", "hot_dog", "pizza", "donut", "cake", "chair", "couch", "potted_plant", "bed",
            "dining_table", "toilet", "tv", "laptop", "mouse", "remote", "keyboard", "cell_phone", "microwave", "oven",
            "toaster", "sink", "refrigerator", "book", "clock", "vase", "scissors", "teddy_bear", "hair_drier", "toothbrush"
    };
    static final Set<String> COCO_FOOD_LABELS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "banana", "apple", "sandwich", "orange", "broccoli", "carrot", "hot_dog", "pizza", "donut", "cake"
    )));

    private CocoFoodBenchmark() {
    }
}
