package com.stopfat.app.data

/**
 * Built-in offline food database. Values per 100 g (or 100 ml), based on typical
 * Polish food composition tables. `unitGrams` is the weight of one typical piece / portion.
 */
object FoodCatalog {

    private fun f(
        id: String, name: String, kcal: Double, p: Double, c: Double, fat: Double,
        cat: String, unitG: Double = 100.0, unit: String = "porcja", vararg aliases: String,
    ) = Food(id, name, kcal, p, c, fat, cat, unitG, unit, aliases.toList())

    val categories = listOf("Nabiał i jaja", "Mięso i ryby", "Pieczywo i zboża", "Warzywa", "Owoce", "Napoje", "Słodycze i przekąski", "Tłuszcze i orzechy", "Dania gotowe", "Fast food")

    val foods: List<Food> = listOf(
        // Nabiał i jaja
        f("egg", "Jajko kurze", 143.0, 12.6, 0.7, 9.9, "Nabiał i jaja", 55.0, "szt", "jajko", "jajka", "jaj", "jajek", "jajecznica", "jajo", "sadzone", "omlet"),
        f("egg_white", "Białko jaja", 52.0, 10.9, 0.7, 0.2, "Nabiał i jaja", 33.0, "szt", "białko jaja", "białka"),
        f("milk2", "Mleko 2%", 51.0, 3.4, 4.9, 2.0, "Nabiał i jaja", 250.0, "szklanka", "mleko", "mlekiem", "mleka"),
        f("milk05", "Mleko 0,5%", 39.0, 3.5, 5.0, 0.5, "Nabiał i jaja", 250.0, "szklanka", "mleko chude"),
        f("milk_oat", "Napój owsiany", 45.0, 0.8, 7.0, 1.5, "Nabiał i jaja", 250.0, "szklanka", "mleko owsiane", "owsiane"),
        f("yogurt_nat", "Jogurt naturalny", 61.0, 4.3, 6.2, 2.0, "Nabiał i jaja", 150.0, "kubek", "jogurt", "jogurtem", "jogurtu"),
        f("yogurt_greek", "Jogurt grecki", 120.0, 4.5, 4.0, 10.0, "Nabiał i jaja", 150.0, "kubek", "grecki"),
        f("skyr", "Skyr naturalny", 63.0, 11.0, 4.0, 0.2, "Nabiał i jaja", 150.0, "kubek", "skyr", "skyru"),
        f("kefir", "Kefir", 50.0, 3.4, 4.5, 2.0, "Nabiał i jaja", 250.0, "szklanka", "kefir", "kefiru", "maślanka"),
        f("cottage", "Serek wiejski", 97.0, 11.0, 2.0, 5.0, "Nabiał i jaja", 200.0, "opakowanie", "serek wiejski", "wiejski", "cottage"),
        f("twarog_pt", "Twaróg półtłusty", 133.0, 18.7, 3.7, 4.7, "Nabiał i jaja", 50.0, "plaster", "twaróg", "twarogu", "twarożek"),
        f("twarog_chudy", "Twaróg chudy", 99.0, 19.8, 3.5, 0.5, "Nabiał i jaja", 50.0, "plaster", "twaróg chudy"),
        f("cheese_gouda", "Ser żółty gouda", 356.0, 25.0, 2.2, 27.0, "Nabiał i jaja", 20.0, "plaster", "ser", "sera", "serem", "gouda", "żółty", "ser żółty"),
        f("mozzarella", "Mozzarella", 254.0, 18.0, 2.5, 19.0, "Nabiał i jaja", 125.0, "kulka", "mozzarella", "mozzarelli"),
        f("feta", "Ser feta", 264.0, 14.0, 4.0, 21.0, "Nabiał i jaja", 30.0, "porcja", "feta", "fety"),
        f("parmesan", "Parmezan", 431.0, 38.0, 4.1, 29.0, "Nabiał i jaja", 10.0, "łyżka", "parmezan"),
        f("butter", "Masło", 735.0, 0.7, 0.7, 82.5, "Tłuszcze i orzechy", 5.0, "plasterek", "masło", "masłem", "masła"),
        f("cream18", "Śmietana 18%", 186.0, 2.5, 3.6, 18.0, "Nabiał i jaja", 15.0, "łyżka", "śmietana", "śmietaną", "śmietany"),
        f("cream_cheese", "Serek kanapkowy", 250.0, 6.0, 3.5, 23.0, "Nabiał i jaja", 15.0, "łyżka", "serek", "almette", "philadelphia"),
        f("homogen", "Serek homogenizowany waniliowy", 165.0, 7.0, 16.0, 8.0, "Nabiał i jaja", 150.0, "kubek", "homogenizowany", "danio"),
        f("protein_pudding", "Pudding proteinowy", 85.0, 10.0, 7.0, 1.5, "Nabiał i jaja", 200.0, "kubek", "pudding"),

        // Mięso i ryby
        f("chicken_breast", "Pierś z kurczaka", 99.0, 21.5, 0.0, 1.3, "Mięso i ryby", 150.0, "filet", "kurczak", "kurczaka", "kurczakiem", "pierś", "piersi", "filet"),
        f("chicken_grill", "Kurczak grillowany", 165.0, 31.0, 0.0, 3.6, "Mięso i ryby", 150.0, "porcja", "grillowany kurczak", "grillowanego"),
        f("chicken_thigh", "Udko z kurczaka", 177.0, 18.0, 0.0, 11.7, "Mięso i ryby", 120.0, "szt", "udko", "udka", "podudzie"),
        f("turkey_breast", "Pierś z indyka", 84.0, 19.2, 0.0, 0.7, "Mięso i ryby", 150.0, "filet", "indyk", "indyka", "indykiem"),
        f("beef_mince", "Wołowina mielona", 215.0, 18.0, 0.0, 16.0, "Mięso i ryby", 100.0, "porcja", "wołowina", "wołowiny", "mielone", "mielona"),
        f("beef_steak", "Stek wołowy", 190.0, 27.0, 0.0, 9.0, "Mięso i ryby", 200.0, "stek", "stek", "steka", "rostbef"),
        f("pork_loin", "Schab wieprzowy", 150.0, 21.0, 0.0, 7.0, "Mięso i ryby", 120.0, "plaster", "schab", "schabu", "wieprzowina"),
        f("pork_chop", "Kotlet schabowy panierowany", 280.0, 18.0, 12.0, 18.0, "Dania gotowe", 150.0, "szt", "schabowy", "schabowego", "kotlet"),
        f("ham", "Szynka drobiowa", 100.0, 18.0, 1.5, 2.5, "Mięso i ryby", 15.0, "plaster", "szynka", "szynki", "szynką", "wędlina"),
        f("ham_pork", "Szynka wieprzowa", 130.0, 19.0, 1.0, 5.5, "Mięso i ryby", 15.0, "plaster", "szynka wieprzowa"),
        f("sausage", "Kiełbasa", 300.0, 14.0, 1.5, 26.0, "Mięso i ryby", 100.0, "porcja", "kiełbasa", "kiełbasy", "kiełbaski", "kabanos"),
        f("parowki", "Parówki", 260.0, 12.0, 2.0, 23.0, "Mięso i ryby", 45.0, "szt", "parówka", "parówki", "parówek"),
        f("bacon", "Boczek", 458.0, 13.0, 0.0, 45.0, "Mięso i ryby", 15.0, "plaster", "boczek", "boczku", "bekon"),
        f("salmon", "Łosoś", 201.0, 20.0, 0.0, 13.6, "Mięso i ryby", 150.0, "filet", "łosoś", "łososia", "łososiem"),
        f("tuna_can", "Tuńczyk w sosie własnym", 116.0, 26.0, 0.0, 1.0, "Mięso i ryby", 120.0, "puszka", "tuńczyk", "tuńczyka", "tuńczykiem"),
        f("cod", "Dorsz", 78.0, 17.7, 0.0, 0.7, "Mięso i ryby", 150.0, "filet", "dorsz", "dorsza", "ryba", "ryby", "rybą"),
        f("shrimp", "Krewetki", 99.0, 24.0, 0.2, 0.3, "Mięso i ryby", 100.0, "porcja", "krewetki", "krewetek"),
        f("herring", "Śledź w oleju", 300.0, 15.0, 1.0, 26.0, "Mięso i ryby", 50.0, "porcja", "śledź", "śledzia"),

        // Pieczywo i zboża
        f("bread_wheat", "Chleb pszenny", 247.0, 8.0, 50.0, 1.5, "Pieczywo i zboża", 35.0, "kromka", "chleb", "chleba", "kromka", "kromki", "kromek", "pieczywo"),
        f("bread_rye", "Chleb żytni razowy", 220.0, 6.0, 43.0, 1.7, "Pieczywo i zboża", 40.0, "kromka", "razowy", "żytni", "razowca", "pełnoziarnisty"),
        f("roll", "Bułka pszenna", 275.0, 8.5, 56.0, 2.0, "Pieczywo i zboża", 60.0, "szt", "bułka", "bułki", "bułkę", "bułek", "kajzerka"),
        f("roll_graham", "Bułka grahamka", 230.0, 9.0, 44.0, 2.5, "Pieczywo i zboża", 70.0, "szt", "grahamka", "grahamki"),
        f("tortilla", "Tortilla pszenna", 310.0, 8.5, 52.0, 7.5, "Pieczywo i zboża", 60.0, "placek", "tortilla", "tortilli", "wrap"),
        f("crispbread", "Pieczywo chrupkie", 370.0, 10.0, 75.0, 2.0, "Pieczywo i zboża", 10.0, "kromka", "wasa", "chrupkie", "wafle ryżowe", "wafel ryżowy"),
        f("oats", "Płatki owsiane", 370.0, 13.0, 60.0, 7.0, "Pieczywo i zboża", 40.0, "porcja", "owsianka", "owsianki", "płatki", "płatków", "owsiane płatki"),
        f("cornflakes", "Płatki kukurydziane", 378.0, 7.0, 84.0, 0.9, "Pieczywo i zboża", 30.0, "porcja", "kukurydziane", "corn flakes"),
        f("granola", "Granola / musli", 450.0, 10.0, 62.0, 17.0, "Pieczywo i zboża", 50.0, "porcja", "granola", "musli", "muesli"),
        f("rice", "Ryż biały gotowany", 130.0, 2.7, 28.0, 0.3, "Pieczywo i zboża", 150.0, "porcja", "ryż", "ryżu", "ryżem"),
        f("rice_brown", "Ryż brązowy gotowany", 123.0, 2.7, 25.6, 1.0, "Pieczywo i zboża", 150.0, "porcja", "ryż brązowy", "brązowy"),
        f("pasta", "Makaron gotowany", 158.0, 5.8, 31.0, 0.9, "Pieczywo i zboża", 180.0, "porcja", "makaron", "makaronu", "makaronem", "spaghetti", "penne"),
        f("pasta_ww", "Makaron pełnoziarnisty gotowany", 149.0, 6.0, 28.0, 1.5, "Pieczywo i zboża", 180.0, "porcja", "makaron pełnoziarnisty"),
        f("buckwheat", "Kasza gryczana gotowana", 110.0, 4.0, 21.0, 1.0, "Pieczywo i zboża", 150.0, "porcja", "kasza", "kaszy", "kaszą", "gryczana", "gryczanej"),
        f("millet", "Kasza jaglana gotowana", 119.0, 3.5, 23.7, 1.0, "Pieczywo i zboża", 150.0, "porcja", "jaglana", "jaglanej", "jaglanka"),
        f("bulgur", "Bulgur gotowany", 83.0, 3.1, 18.6, 0.2, "Pieczywo i zboża", 150.0, "porcja", "bulgur", "kuskus"),
        f("quinoa", "Komosa ryżowa gotowana", 120.0, 4.4, 21.3, 1.9, "Pieczywo i zboża", 150.0, "porcja", "komosa", "quinoa"),
        f("potato", "Ziemniaki gotowane", 77.0, 1.9, 17.0, 0.1, "Warzywa", 150.0, "porcja", "ziemniak", "ziemniaki", "ziemniaków", "kartofle"),
        f("sweet_potato", "Batat pieczony", 90.0, 2.0, 20.7, 0.2, "Warzywa", 150.0, "szt", "batat", "bataty", "batata"),
        f("fries", "Frytki", 312.0, 3.4, 41.0, 15.0, "Fast food", 150.0, "porcja", "frytki", "frytek"),

        // Warzywa
        f("tomato", "Pomidor", 18.0, 0.9, 3.9, 0.2, "Warzywa", 120.0, "szt", "pomidor", "pomidora", "pomidory", "pomidorem"),
        f("cucumber", "Ogórek", 15.0, 0.7, 3.6, 0.1, "Warzywa", 150.0, "szt", "ogórek", "ogórka", "ogórki", "mizeria"),
        f("pickle", "Ogórek kiszony", 12.0, 0.5, 2.2, 0.2, "Warzywa", 60.0, "szt", "kiszony", "kiszone", "kiszonego"),
        f("pepper", "Papryka czerwona", 31.0, 1.0, 6.0, 0.3, "Warzywa", 150.0, "szt", "papryka", "papryki", "papryką"),
        f("lettuce", "Sałata", 15.0, 1.4, 2.9, 0.2, "Warzywa", 50.0, "porcja", "sałata", "sałaty", "sałatą", "rukola", "roszponka"),
        f("salad_mix", "Sałatka warzywna (bez sosu)", 25.0, 1.2, 4.5, 0.3, "Warzywa", 200.0, "miska", "sałatka", "sałatki", "sałatkę"),
        f("carrot", "Marchew", 41.0, 0.9, 9.6, 0.2, "Warzywa", 80.0, "szt", "marchew", "marchewka", "marchewki", "marchwi"),
        f("broccoli", "Brokuły", 34.0, 2.8, 6.6, 0.4, "Warzywa", 150.0, "porcja", "brokuł", "brokuły", "brokułów", "brokułami"),
        f("cauliflower", "Kalafior", 25.0, 1.9, 5.0, 0.3, "Warzywa", 150.0, "porcja", "kalafior", "kalafiora"),
        f("spinach", "Szpinak", 23.0, 2.9, 3.6, 0.4, "Warzywa", 100.0, "porcja", "szpinak", "szpinaku", "szpinakiem"),
        f("onion", "Cebula", 40.0, 1.1, 9.3, 0.1, "Warzywa", 100.0, "szt", "cebula", "cebuli", "cebulą", "cebulka"),
        f("zucchini", "Cukinia", 17.0, 1.2, 3.1, 0.3, "Warzywa", 200.0, "szt", "cukinia", "cukinii"),
        f("mushrooms", "Pieczarki", 22.0, 3.1, 3.3, 0.3, "Warzywa", 100.0, "porcja", "pieczarki", "pieczarek", "grzyby"),
        f("corn", "Kukurydza konserwowa", 86.0, 3.2, 19.0, 1.2, "Warzywa", 70.0, "porcja", "kukurydza", "kukurydzy"),
        f("beans", "Fasola czerwona (konserwa)", 100.0, 7.0, 16.0, 0.5, "Warzywa", 100.0, "porcja", "fasola", "fasoli", "fasolka"),
        f("chickpeas", "Ciecierzyca gotowana", 164.0, 8.9, 27.4, 2.6, "Warzywa", 100.0, "porcja", "ciecierzyca", "ciecierzycy", "hummus"),
        f("lentils", "Soczewica gotowana", 116.0, 9.0, 20.0, 0.4, "Warzywa", 150.0, "porcja", "soczewica", "soczewicy"),
        f("peas", "Groszek zielony", 81.0, 5.4, 14.5, 0.4, "Warzywa", 80.0, "porcja", "groszek", "groszku"),
        f("avocado", "Awokado", 160.0, 2.0, 8.5, 14.7, "Owoce", 150.0, "szt", "awokado"),
        f("tofu", "Tofu naturalne", 120.0, 12.0, 2.0, 7.0, "Warzywa", 100.0, "porcja", "tofu"),

        // Owoce
        f("apple", "Jabłko", 52.0, 0.3, 13.8, 0.2, "Owoce", 180.0, "szt", "jabłko", "jabłka", "jabłek", "jabłkiem"),
        f("banana", "Banan", 89.0, 1.1, 22.8, 0.3, "Owoce", 120.0, "szt", "banan", "banana", "banany", "bananem", "bananów"),
        f("orange", "Pomarańcza", 47.0, 0.9, 11.8, 0.1, "Owoce", 200.0, "szt", "pomarańcza", "pomarańczy", "pomarańcze"),
        f("mandarin", "Mandarynka", 53.0, 0.8, 13.3, 0.3, "Owoce", 70.0, "szt", "mandarynka", "mandarynki", "mandarynek"),
        f("strawberries", "Truskawki", 32.0, 0.7, 7.7, 0.3, "Owoce", 150.0, "garść", "truskawki", "truskawek", "truskawkami"),
        f("blueberries", "Borówki", 57.0, 0.7, 14.5, 0.3, "Owoce", 100.0, "garść", "borówki", "borówek", "jagody"),
        f("raspberries", "Maliny", 52.0, 1.2, 11.9, 0.7, "Owoce", 100.0, "garść", "maliny", "malin"),
        f("grapes", "Winogrona", 69.0, 0.7, 18.1, 0.2, "Owoce", 150.0, "garść", "winogrona", "winogron"),
        f("pear", "Gruszka", 57.0, 0.4, 15.2, 0.1, "Owoce", 180.0, "szt", "gruszka", "gruszki", "gruszek"),
        f("kiwi", "Kiwi", 61.0, 1.1, 14.7, 0.5, "Owoce", 75.0, "szt", "kiwi"),
        f("watermelon", "Arbuz", 30.0, 0.6, 7.6, 0.2, "Owoce", 300.0, "plaster", "arbuz", "arbuza"),
        f("mango", "Mango", 60.0, 0.8, 15.0, 0.4, "Owoce", 200.0, "szt", "mango"),
        f("pineapple", "Ananas", 50.0, 0.5, 13.1, 0.1, "Owoce", 150.0, "porcja", "ananas", "ananasa"),
        f("plum", "Śliwka", 46.0, 0.7, 11.4, 0.3, "Owoce", 40.0, "szt", "śliwka", "śliwki", "śliwek"),
        f("dates", "Daktyle suszone", 282.0, 2.5, 75.0, 0.4, "Owoce", 8.0, "szt", "daktyl", "daktyle", "daktyli"),
        f("raisins", "Rodzynki", 299.0, 3.1, 79.0, 0.5, "Owoce", 30.0, "garść", "rodzynki", "rodzynek"),

        // Tłuszcze i orzechy
        f("olive_oil", "Oliwa z oliwek", 884.0, 0.0, 0.0, 100.0, "Tłuszcze i orzechy", 10.0, "łyżka", "oliwa", "oliwy", "oliwą", "olej", "oleju", "olejem"),
        f("peanut_butter", "Masło orzechowe", 588.0, 25.0, 20.0, 50.0, "Tłuszcze i orzechy", 15.0, "łyżka", "masło orzechowe", "orzechowe"),
        f("almonds", "Migdały", 579.0, 21.0, 22.0, 50.0, "Tłuszcze i orzechy", 30.0, "garść", "migdały", "migdałów"),
        f("walnuts", "Orzechy włoskie", 654.0, 15.0, 14.0, 65.0, "Tłuszcze i orzechy", 30.0, "garść", "orzechy", "orzechów", "włoskie"),
        f("cashews", "Orzechy nerkowca", 553.0, 18.0, 30.0, 44.0, "Tłuszcze i orzechy", 30.0, "garść", "nerkowce", "nerkowca"),
        f("peanuts", "Orzeszki ziemne", 567.0, 26.0, 16.0, 49.0, "Tłuszcze i orzechy", 30.0, "garść", "orzeszki", "fistaszki"),
        f("chia", "Nasiona chia", 486.0, 17.0, 42.0, 31.0, "Tłuszcze i orzechy", 15.0, "łyżka", "chia"),
        f("mayo", "Majonez", 680.0, 1.0, 1.0, 75.0, "Tłuszcze i orzechy", 15.0, "łyżka", "majonez", "majonezu", "majonezem"),
        f("ketchup", "Ketchup", 112.0, 1.3, 26.0, 0.2, "Tłuszcze i orzechy", 15.0, "łyżka", "ketchup", "keczup", "ketchupem"),
        f("hummus_spread", "Hummus", 166.0, 8.0, 14.0, 9.6, "Tłuszcze i orzechy", 30.0, "łyżka", "hummusem"),

        // Napoje
        f("water", "Woda", 0.0, 0.0, 0.0, 0.0, "Napoje", 250.0, "szklanka", "woda", "wody"),
        f("coffee_black", "Kawa czarna", 2.0, 0.3, 0.0, 0.0, "Napoje", 250.0, "kubek", "kawa", "kawy", "kawę", "espresso", "americano"),
        f("latte", "Kawa latte", 56.0, 3.0, 4.8, 2.6, "Napoje", 300.0, "kubek", "latte", "cappuccino", "flat white"),
        f("tea", "Herbata bez cukru", 1.0, 0.0, 0.2, 0.0, "Napoje", 250.0, "kubek", "herbata", "herbaty", "herbatę"),
        f("sugar", "Cukier", 400.0, 0.0, 100.0, 0.0, "Słodycze i przekąski", 5.0, "łyżeczka", "cukier", "cukru", "cukrem"),
        f("honey", "Miód", 320.0, 0.3, 80.0, 0.0, "Słodycze i przekąski", 12.0, "łyżeczka", "miód", "miodu", "miodem"),
        f("juice_orange", "Sok pomarańczowy", 45.0, 0.7, 10.4, 0.2, "Napoje", 250.0, "szklanka", "sok", "soku", "soki"),
        f("cola", "Cola", 42.0, 0.0, 10.6, 0.0, "Napoje", 330.0, "puszka", "cola", "coca", "pepsi", "napój gazowany"),
        f("cola_zero", "Cola zero", 0.3, 0.0, 0.0, 0.0, "Napoje", 330.0, "puszka", "zero", "light"),
        f("beer", "Piwo jasne", 43.0, 0.5, 3.6, 0.0, "Napoje", 500.0, "butelka", "piwo", "piwa"),
        f("wine", "Wino czerwone wytrawne", 85.0, 0.1, 2.6, 0.0, "Napoje", 150.0, "kieliszek", "wino", "wina"),
        f("vodka", "Wódka", 231.0, 0.0, 0.0, 0.0, "Napoje", 50.0, "kieliszek", "wódka", "wódki"),
        f("energy", "Napój energetyczny", 45.0, 0.0, 11.0, 0.0, "Napoje", 250.0, "puszka", "energetyk", "red bull", "monster"),
        f("protein_shake", "Odżywka białkowa (porcja na wodzie)", 380.0, 75.0, 8.0, 6.0, "Napoje", 30.0, "miarka", "odżywka", "białko w proszku", "shake", "szejk", "whey"),
        f("smoothie", "Smoothie owocowe", 55.0, 0.8, 12.5, 0.3, "Napoje", 300.0, "kubek", "smoothie", "koktajl"),

        // Słodycze i przekąski
        f("chocolate_milk", "Czekolada mleczna", 535.0, 7.6, 59.0, 30.0, "Słodycze i przekąski", 25.0, "4 kostki", "czekolada", "czekolady", "czekoladę", "kostka czekolady"),
        f("chocolate_dark", "Czekolada gorzka 70%", 580.0, 8.0, 36.0, 43.0, "Słodycze i przekąski", 25.0, "4 kostki", "gorzka"),
        f("chips", "Chipsy ziemniaczane", 536.0, 7.0, 53.0, 34.0, "Słodycze i przekąski", 30.0, "garść", "chipsy", "chipsów", "czipsy"),
        f("cookies", "Ciastka maślane", 480.0, 6.5, 68.0, 20.0, "Słodycze i przekąski", 10.0, "szt", "ciastko", "ciastka", "ciasteczka", "herbatniki"),
        f("cake", "Ciasto (sernik / szarlotka)", 320.0, 6.0, 38.0, 16.0, "Słodycze i przekąski", 120.0, "kawałek", "ciasto", "ciasta", "sernik", "szarlotka", "kawałek ciasta"),
        f("donut", "Pączek", 410.0, 6.0, 48.0, 21.0, "Słodycze i przekąski", 70.0, "szt", "pączek", "pączka", "pączki"),
        f("ice_cream", "Lody śmietankowe", 207.0, 3.5, 24.0, 11.0, "Słodycze i przekąski", 100.0, "gałka x2", "lody", "lodów", "lód"),
        f("bar_candy", "Baton czekoladowy", 480.0, 5.0, 62.0, 23.0, "Słodycze i przekąski", 50.0, "szt", "baton", "batonik", "snickers", "mars", "twix"),
        f("bar_protein", "Baton proteinowy", 350.0, 30.0, 35.0, 10.0, "Słodycze i przekąski", 60.0, "szt", "baton proteinowy", "proteinowy"),
        f("gummies", "Żelki", 340.0, 6.0, 77.0, 0.2, "Słodycze i przekąski", 25.0, "garść", "żelki", "żelków"),
        f("popcorn", "Popcorn", 387.0, 12.0, 78.0, 4.5, "Słodycze i przekąski", 30.0, "miska", "popcorn"),
        f("jam", "Dżem", 250.0, 0.4, 60.0, 0.1, "Słodycze i przekąski", 15.0, "łyżka", "dżem", "dżemem", "konfitura"),
        f("nutella", "Krem czekoladowo-orzechowy", 539.0, 6.3, 57.5, 30.9, "Słodycze i przekąski", 15.0, "łyżka", "nutella", "nutellą", "krem orzechowy"),

        // Dania gotowe
        f("pierogi_ruskie", "Pierogi ruskie", 215.0, 6.5, 33.0, 6.0, "Dania gotowe", 45.0, "szt", "pierogi", "pierogów", "pieróg", "ruskie"),
        f("pierogi_meat", "Pierogi z mięsem", 230.0, 10.0, 28.0, 8.5, "Dania gotowe", 45.0, "szt", "pierogi z mięsem"),
        f("rosol", "Rosół z makaronem", 45.0, 3.0, 4.5, 1.6, "Dania gotowe", 350.0, "talerz", "rosół", "rosołu", "zupa"),
        f("zurek", "Żurek z jajkiem i kiełbasą", 80.0, 4.0, 5.0, 5.0, "Dania gotowe", 400.0, "talerz", "żurek", "żurku", "barszcz"),
        f("pomidorowa", "Zupa pomidorowa", 55.0, 1.5, 7.5, 2.0, "Dania gotowe", 350.0, "talerz", "pomidorowa", "pomidorowej"),
        f("bigos", "Bigos", 135.0, 7.0, 6.0, 9.0, "Dania gotowe", 300.0, "porcja", "bigos", "bigosu"),
        f("golabki", "Gołąbki w sosie pomidorowym", 120.0, 6.5, 10.0, 6.0, "Dania gotowe", 250.0, "2 szt", "gołąbki", "gołąbków"),
        f("nalesniki", "Naleśniki z serem", 210.0, 8.5, 27.0, 7.5, "Dania gotowe", 120.0, "szt", "naleśnik", "naleśniki", "naleśników"),
        f("placki", "Placki ziemniaczane", 220.0, 3.5, 25.0, 12.0, "Dania gotowe", 80.0, "szt", "placki", "placek", "placków"),
        f("spaghetti_bol", "Spaghetti bolognese", 150.0, 7.5, 18.0, 5.0, "Dania gotowe", 350.0, "talerz", "bolognese", "spaghetti bolognese"),
        f("lasagne", "Lasagne", 160.0, 8.5, 13.5, 8.0, "Dania gotowe", 350.0, "porcja", "lasagne", "lazania"),
        f("risotto", "Risotto z kurczakiem", 140.0, 7.0, 18.0, 4.5, "Dania gotowe", 350.0, "porcja", "risotto"),
        f("curry", "Curry z kurczakiem i ryżem", 150.0, 9.0, 16.0, 5.5, "Dania gotowe", 400.0, "porcja", "curry"),
        f("sushi", "Sushi (maki/nigiri)", 150.0, 6.0, 28.0, 1.5, "Dania gotowe", 30.0, "kawałek", "sushi"),
        f("salad_caesar", "Sałatka Cezar", 140.0, 9.0, 6.0, 9.0, "Dania gotowe", 300.0, "miska", "cezar", "caesar"),
        f("sandwich", "Kanapka z szynką i serem", 240.0, 12.0, 25.0, 10.0, "Dania gotowe", 120.0, "szt", "kanapka", "kanapki", "kanapek", "tost", "tosty"),

        // Fast food
        f("pizza", "Pizza margherita", 266.0, 11.0, 33.0, 10.0, "Fast food", 110.0, "kawałek", "pizza", "pizzy", "pizzę"),
        f("pizza_pep", "Pizza pepperoni", 290.0, 12.0, 32.0, 13.0, "Fast food", 115.0, "kawałek", "pepperoni", "salami"),
        f("burger", "Hamburger", 254.0, 13.0, 27.0, 10.0, "Fast food", 220.0, "szt", "burger", "burgera", "hamburger", "cheeseburger"),
        f("kebab", "Kebab w bułce", 215.0, 11.0, 20.0, 10.0, "Fast food", 400.0, "szt", "kebab", "kebaba", "döner", "durum"),
        f("hotdog", "Hot dog", 260.0, 9.5, 25.0, 14.0, "Fast food", 130.0, "szt", "hot dog", "hotdog", "hot-dog"),
        f("nuggets", "Nuggetsy z kurczaka", 296.0, 15.0, 18.0, 18.0, "Fast food", 18.0, "szt", "nuggetsy", "nuggets", "nuggetsów"),
        f("zapiekanka", "Zapiekanka", 230.0, 9.0, 28.0, 9.0, "Fast food", 300.0, "szt", "zapiekanka", "zapiekanki"),
        f("falafel", "Falafel", 333.0, 13.3, 31.8, 17.8, "Fast food", 17.0, "szt", "falafel", "falafele"),
    ).map { it.copy(gi = com.stopfat.app.domain.Glycemic.giById[it.id]) }

    private val byId = foods.associateBy { it.id }

    fun byId(id: String): Food? = byId[id]

    /** Lower-case and strip Polish diacritics, so "ryz" finds "ryż" and "maslo" finds "masło". */
    fun normalize(s: String): String = s.lowercase()
        .replace('ą', 'a').replace('ć', 'c').replace('ę', 'e').replace('ł', 'l').replace('ń', 'n')
        .replace('ó', 'o').replace('ś', 's').replace('ź', 'z').replace('ż', 'z')

    fun search(query: String, extra: List<Food> = emptyList()): List<Food> {
        val q = normalize(query.trim())
        val all = extra + foods
        if (q.isEmpty()) return all
        return all.map { it to score(it, q) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    private fun score(food: Food, q: String): Int {
        val name = normalize(food.name)
        val aliases = food.aliases.map(::normalize)
        if (name == q) return 100
        if (name.startsWith(q)) return 80
        if (aliases.any { it == q }) return 75
        if (name.contains(q)) return 60
        if (aliases.any { it.startsWith(q) }) return 50
        val qWords = q.split(" ").filter { it.length >= 3 }
        if (qWords.isNotEmpty() && qWords.all { w -> name.contains(w.take(4)) || aliases.any { it.contains(w.take(4)) } }) return 30
        return 0
    }
}
