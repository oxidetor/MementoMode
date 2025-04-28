package com.example.appblocker.calculator

import android.view.View

/**
 * Interface defining the contract for calculator page setup and data handling.
 * Following the Interface Segregation Principle of SOLID.
 */
interface CalculatorPageHandler {
    /**
     * Sets up the page by finding and initializing views, setting up event listeners, etc.
     * @param pageView The page view to set up
     * @return true if setup was successful, false otherwise
     */
    fun setupPage(pageView: View): Boolean
    
    /**
     * Updates UI elements on the page based on current data.
     * @param pageView The page view to update
     */
    fun updatePageUI(pageView: View)
    
    /**
     * Saves any data entered or selected on this page.
     * @param pageView The page view to save data from
     */
    fun savePageData(pageView: View)
} 