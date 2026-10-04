package org.androshogi.ui.main;

import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.matcher.RootMatchers.isPlatformPopup;

import android.view.MenuItem;

import androidx.test.espresso.DataInteraction;
import androidx.test.espresso.matcher.BoundedMatcher;

import org.hamcrest.Description;

/** Finds popup items through their adapter so Espresso scrolls them into view. */
final class MainActivityMenuActions {
    private MainActivityMenuActions() {}

    static DataInteraction menuItem(int itemId) {
        return onData(new BoundedMatcher<Object, MenuItem>(MenuItem.class) {
            @Override
            protected boolean matchesSafely(MenuItem item) {
                return item.getItemId() == itemId;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("menu item with ID ").appendValue(itemId);
            }
        }).inRoot(isPlatformPopup());
    }
}
