# Memento Mode Onboarding Module

## Architecture Overview

The onboarding module follows SOLID principles for a maintainable and extensible design:

### Calculator Pages

1. **Estimate Input** (`page_calculator_estimate_input.xml`): Users input their estimated daily screen time
2. **Lifetime Impact** (`page_calculator_lifetime_impact.xml`): Shows projected lifetime hours spent on phone
3. **Usage Breakdown** (`page_calculator_usage_breakdown.xml`): Compares estimated vs. actual usage with categories
4. **Goal Setting** (`page_calculator_goal_setting.xml`): Users set a target for daily screen time
5. **Habit Selection** (`page_calculator_habit_selection.xml`): Selection of alternative activities to pursue

### Key Components

- **CalculatorPage**: Data class that encapsulates layout resource ID and semantic identifier
- **CalculatorPageHandler**: Interface defining the contract for page setup and data handling
- **ScreentimeCalculatorActivity**: Main coordinator that manages page navigation
- **CalculatorPagerAdapter**: Adapter that loads the appropriate layouts for each page

### Page Handlers

Each page has a dedicated handler class implementing the CalculatorPageHandler interface:

- **EstimateInputHandler**: Manages the first page where users input their estimated screen time
- **LifetimeImpactHandler**: Displays and animates lifetime usage calculations
- **UsageBreakdownHandler**: Shows actual usage stats by category
- **GoalSettingHandler**: Handles goal selection and calculates time savings
- **HabitSelectionHandler**: Manages the alternative activities selection

## SOLID Principles Applied

1. **Single Responsibility Principle**: Each handler class is responsible for one specific page
2. **Open/Closed Principle**: The design is open for extension but closed for modification
3. **Liskov Substitution Principle**: Any CalculatorPageHandler can be used interchangeably
4. **Interface Segregation Principle**: The interface defines only the methods needed
5. **Dependency Inversion Principle**: High-level and low-level modules depend on abstractions

## Usage Flow

1. The activity loads the first page
2. Each page's setup and UI update is handled by its dedicated handler
3. As the user navigates, the appropriate handler manages the page's functionality
4. Data is saved before moving to the next page
5. Upon completion, the data is stored in SharedPreferences and used throughout the app 