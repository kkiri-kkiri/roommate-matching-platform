-- Run once against an existing database before starting the updated application.
-- Fresh databases created from the current entities do not need this script.
ALTER TABLE PREFERENCE_CRITERION DROP COLUMN is_required;
ALTER TABLE ROOMMATE_MATCH DROP COLUMN required_conditions_met;
