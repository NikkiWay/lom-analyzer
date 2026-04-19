# Holiday Files Methodology

## Adding New Yearly Holiday Files

1. Create a new file at `src/main/resources/resources/holidays/v{YYYY}.json`
2. Follow the schema:
```json
{
  "version": "v{YYYY}",
  "valid_from": "{YYYY}-01-01",
  "valid_to": "{YYYY}-12-31",
  "holidays": [
    {"date": "{YYYY}-01-01", "name": "Новый год"},
    ...
  ]
}
```
3. Include all Russian public holidays per the labor code
4. Run `./gradlew build` to verify the file parses correctly
5. The HolidayCalendar.loadForSession() will automatically pick up new files

## Standard Russian Public Holidays

- January 1-8: New Year holidays + Christmas
- February 23: Defender of the Fatherland Day
- March 8: International Women's Day
- May 1: Spring and Labor Day
- May 9: Victory Day
- June 12: Russia Day
- November 4: National Unity Day

## Coverage Flags

If a session window extends beyond available holiday files:
- `holidaysPartialCoverageFlag = true` is set on the session
- Session Quality Score `q_temporal_stability` is reduced by 0.2
