package co.edu.icesi.pdg.mte.common;

public final class CacheNames {
    public static final String CATALOG_BOOTSTRAP = "catalogBootstrap";
    public static final String MEASUREMENT_UNITS = "measurementUnits";
    public static final String ACADEMIC_PERIODS = "academicPeriods";
    public static final String DEPARTMENTS = "departments";
    public static final String SCHOOLS = "schools";
    public static final String WORLDS = "worlds";
    public static final String STRATEGIC_BET_CATALOG = "strategicBetCatalog";
    public static final String GOAL_CATALOG = "goalCatalog";

    public static final String[] ALL_CATALOGS = {
            CATALOG_BOOTSTRAP,
            MEASUREMENT_UNITS,
            ACADEMIC_PERIODS,
            DEPARTMENTS,
            SCHOOLS,
            WORLDS,
            STRATEGIC_BET_CATALOG,
            GOAL_CATALOG
    };

    private CacheNames() {
    }
}
