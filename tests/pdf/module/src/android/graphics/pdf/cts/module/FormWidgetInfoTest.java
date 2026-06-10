/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.graphics.pdf.cts.module;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

import android.graphics.Rect;
import android.graphics.pdf.models.FormWidgetInfo;
import android.graphics.pdf.models.ListItem;
import android.os.Parcel;

import org.junit.Test;

import java.util.List;

public class FormWidgetInfoTest {
    @Test
    public void testPushButton() {
        Rect widgetRect = new Rect(100, 0, 150, 50);
        String textValue = "Reset";
        String a11yLabel = "Reset button";
        FormWidgetInfo pushButton = new FormWidgetInfo.Builder(
                /* widgetType= */ FormWidgetInfo.WIDGET_TYPE_PUSHBUTTON,
                /* widgetIndex= */ 4, widgetRect, textValue, a11yLabel).build();

        // Required properties
        assertThat(pushButton.getWidgetType()).isEqualTo(FormWidgetInfo.WIDGET_TYPE_PUSHBUTTON);
        assertThat(pushButton.getWidgetIndex()).isEqualTo(4);
        assertThat(pushButton.getWidgetRect()).isEqualTo(widgetRect);
        assertThat(pushButton.getTextValue()).isEqualTo(textValue);
        assertThat(pushButton.getAccessibilityLabel()).isEqualTo(a11yLabel);

        // Default properties
        assertThat(pushButton.isReadOnly()).isFalse();

        // Inapplicable properties
        assertThat(pushButton.isEditableText()).isFalse();
        assertThat(pushButton.isMultiSelect()).isFalse();
        assertThat(pushButton.isMultiLineText()).isFalse();
        assertThat(pushButton.getMaxLength()).isEqualTo(-1);
        assertThat(pushButton.getFontSize()).isEqualTo(0f);
        assertThat(pushButton.getListItems()).isEmpty();
    }

    @Test
    public void testPushButton_readOnly() {
        Rect widgetRect = new Rect(150, 210, 225, 260);
        String textValue = "Disabled";
        String a11yLabel = "Disabled button";
        FormWidgetInfo pushButton = new FormWidgetInfo.Builder(
                /* widgetType= */ FormWidgetInfo.WIDGET_TYPE_PUSHBUTTON,
                /* widgetIndex= */ 2, widgetRect, textValue, a11yLabel).setReadOnly(true).build();

        // Required properties
        assertThat(pushButton.getWidgetType()).isEqualTo(FormWidgetInfo.WIDGET_TYPE_PUSHBUTTON);
        assertThat(pushButton.getWidgetIndex()).isEqualTo(2);
        assertThat(pushButton.getWidgetRect()).isEqualTo(widgetRect);
        assertThat(pushButton.getTextValue()).isEqualTo(textValue);
        assertThat(pushButton.getAccessibilityLabel()).isEqualTo(a11yLabel);

        assertThat(pushButton.isReadOnly()).isTrue();

        // Inapplicable properties
        assertThat(pushButton.isEditableText()).isFalse();
        assertThat(pushButton.isMultiSelect()).isFalse();
        assertThat(pushButton.isMultiLineText()).isFalse();
        assertThat(pushButton.getMaxLength()).isEqualTo(-1);
        assertThat(pushButton.getFontSize()).isEqualTo(0f);
        assertThat(pushButton.getListItems()).isEmpty();
    }

    @Test
    public void testCheckBox() {
        Rect widgetRect = new Rect(0, 0, 50, 50);
        String textValue = "False";
        String a11yLabel = "Checkbox";
        FormWidgetInfo checkBox = new FormWidgetInfo.Builder(
                /* widgetType= */ FormWidgetInfo.WIDGET_TYPE_CHECKBOX,
                /* widgetIndex= */ 3, widgetRect, textValue, a11yLabel).build();

        // Required properties
        assertThat(checkBox.getWidgetType()).isEqualTo(FormWidgetInfo.WIDGET_TYPE_CHECKBOX);
        assertThat(checkBox.getWidgetIndex()).isEqualTo(3);
        assertThat(checkBox.getWidgetRect()).isEqualTo(widgetRect);
        assertThat(checkBox.getTextValue()).isEqualTo(textValue);
        assertThat(checkBox.getAccessibilityLabel()).isEqualTo(a11yLabel);

        // Default properties
        assertThat(checkBox.isReadOnly()).isFalse();

        // Inapplicable properties
        assertThat(checkBox.isEditableText()).isFalse();
        assertThat(checkBox.isMultiSelect()).isFalse();
        assertThat(checkBox.isMultiLineText()).isFalse();
        assertThat(checkBox.getMaxLength()).isEqualTo(-1);
        assertThat(checkBox.getFontSize()).isEqualTo(0f);
        assertThat(checkBox.getListItems()).isEmpty();
    }

    @Test
    public void testCheckBox_readOnly() {
        Rect widgetRect = new Rect(10, 50, 160, 200);
        String textValue = "Checkbox";
        String a11yLabel = "Disabled button";
        FormWidgetInfo readOnlyCheckbox = new FormWidgetInfo.Builder(
                /* widgetType= */ FormWidgetInfo.WIDGET_TYPE_CHECKBOX,
                /* widgetIndex= */ 0, widgetRect, textValue, a11yLabel).setReadOnly(true).build();

        // Required properties
        assertThat(readOnlyCheckbox.getWidgetType()).isEqualTo(FormWidgetInfo.WIDGET_TYPE_CHECKBOX);
        assertThat(readOnlyCheckbox.getWidgetIndex()).isEqualTo(0);
        assertThat(readOnlyCheckbox.getWidgetRect()).isEqualTo(widgetRect);
        assertThat(readOnlyCheckbox.getTextValue()).isEqualTo(textValue);
        assertThat(readOnlyCheckbox.getAccessibilityLabel()).isEqualTo(a11yLabel);

        assertThat(readOnlyCheckbox.isReadOnly()).isTrue();

        // Inapplicable properties
        assertThat(readOnlyCheckbox.isEditableText()).isFalse();
        assertThat(readOnlyCheckbox.isMultiSelect()).isFalse();
        assertThat(readOnlyCheckbox.isMultiLineText()).isFalse();
        assertThat(readOnlyCheckbox.getMaxLength()).isEqualTo(-1);
        assertThat(readOnlyCheckbox.getFontSize()).isEqualTo(0f);
        assertThat(readOnlyCheckbox.getListItems()).isEmpty();
    }

    @Test
    public void testRadioButton() {
        Rect widgetRect = new Rect(330, 655, 390, 715);
        String textValue = "False";
        String a11yLabel = "RadioButton";
        FormWidgetInfo radioButton = new FormWidgetInfo.Builder(
                /* widgetType= */ FormWidgetInfo.WIDGET_TYPE_RADIOBUTTON,
                /* widgetIndex= */ 9, widgetRect, textValue, a11yLabel).build();

        // Required properties
        assertThat(radioButton.getWidgetType()).isEqualTo(FormWidgetInfo.WIDGET_TYPE_RADIOBUTTON);
        assertThat(radioButton.getWidgetIndex()).isEqualTo(9);
        assertThat(radioButton.getWidgetRect()).isEqualTo(widgetRect);
        assertThat(radioButton.getTextValue()).isEqualTo(textValue);
        assertThat(radioButton.getAccessibilityLabel()).isEqualTo(a11yLabel);

        // Default properties
        assertThat(radioButton.isReadOnly()).isFalse();

        // Inapplicable properties
        assertThat(radioButton.isEditableText()).isFalse();
        assertThat(radioButton.isMultiSelect()).isFalse();
        assertThat(radioButton.isMultiLineText()).isFalse();
        assertThat(radioButton.getMaxLength()).isEqualTo(-1);
        assertThat(radioButton.getFontSize()).isEqualTo(0f);
        assertThat(radioButton.getListItems()).isEmpty();
    }

    @Test
    public void testRadioButton_readOnly() {
        Rect widgetRect = new Rect(1000, 1500, 1100, 1600);
        String textValue = "False";
        String a11yLabel = "RadioButton read only";
        FormWidgetInfo readOnlyRadioButton = new FormWidgetInfo.Builder(
                /* widgetType= */ FormWidgetInfo.WIDGET_TYPE_RADIOBUTTON,
                /* widgetIndex= */ 25, widgetRect, textValue, a11yLabel).setReadOnly(true).build();

        // Required properties
        assertThat(readOnlyRadioButton.getWidgetType()).isEqualTo(
                FormWidgetInfo.WIDGET_TYPE_RADIOBUTTON);
        assertThat(readOnlyRadioButton.getWidgetIndex()).isEqualTo(25);
        assertThat(readOnlyRadioButton.getWidgetRect()).isEqualTo(widgetRect);
        assertThat(readOnlyRadioButton.getTextValue()).isEqualTo(textValue);
        assertThat(readOnlyRadioButton.getAccessibilityLabel()).isEqualTo(a11yLabel);

        assertThat(readOnlyRadioButton.isReadOnly()).isTrue();

        // Inapplicable properties
        assertThat(readOnlyRadioButton.isEditableText()).isFalse();
        assertThat(readOnlyRadioButton.isMultiSelect()).isFalse();
        assertThat(readOnlyRadioButton.isMultiLineText()).isFalse();
        assertThat(readOnlyRadioButton.getMaxLength()).isEqualTo(-1);
        assertThat(readOnlyRadioButton.getFontSize()).isEqualTo(0f);
        assertThat(readOnlyRadioButton.getListItems()).isEmpty();
    }

    @Test
    public void testCombobox() {
        List<ListItem> listItems = List.of(new ListItem("The", /* selected= */ false),
                new ListItem("quick", /* selected= */ true),
                new ListItem("brown", /* selected= */ false),
                new ListItem("fox", /* selected= */ false),
                new ListItem("jumped", /* selected= */ false),
                new ListItem("over", /* selected= */ false),
                new ListItem("the", /* selected= */ false),
                new ListItem("lazy", /* selected= */ false),
                new ListItem("dog", /* selected= */ false));
        Rect widgetRect = new Rect(1250, 0, 1500, 250);
        String textValue = "quick";
        String a11yLabel = "Combobox single select";
        FormWidgetInfo comboBox = new FormWidgetInfo.Builder(
                /* widgetType= */ FormWidgetInfo.WIDGET_TYPE_COMBOBOX,
                /* widgetIndex= */ 1, widgetRect, textValue, a11yLabel).setListItems(
                listItems).build();

        // Required properties
        assertThat(comboBox.getWidgetType()).isEqualTo(FormWidgetInfo.WIDGET_TYPE_COMBOBOX);
        assertThat(comboBox.getWidgetIndex()).isEqualTo(1);
        assertThat(comboBox.getWidgetRect()).isEqualTo(widgetRect);
        assertThat(comboBox.getTextValue()).isEqualTo(textValue);
        assertThat(comboBox.getAccessibilityLabel()).isEqualTo(a11yLabel);
        assertThat(comboBox.getListItems()).isEqualTo(listItems);

        // Default properties
        assertThat(comboBox.isReadOnly()).isFalse();
        assertThat(comboBox.isEditableText()).isFalse();
        assertThat(comboBox.getFontSize()).isEqualTo(0f);

        // Inapplicable properties
        assertThat(comboBox.isMultiSelect()).isFalse();
        assertThat(comboBox.isMultiLineText()).isFalse();
        assertThat(comboBox.getMaxLength()).isEqualTo(-1);
    }

    @Test
    public void testCombobox_readOnly() {
        List<ListItem> listItems = List.of(new ListItem("Eggs", /* selected= */ true),
                new ListItem("and", /* selected= */ false),
                new ListItem("Bacon", /* selected= */ false));
        Rect widgetRect = new Rect(2360, 355, 2660, 655);
        String textValue = "Eggs";
        String a11yLabel = "Combobox read only";
        FormWidgetInfo readOnlyCombobox = new FormWidgetInfo.Builder(
                /* widgetType= */ FormWidgetInfo.WIDGET_TYPE_COMBOBOX,
                /* widgetIndex= */ 15, widgetRect, textValue, a11yLabel).setReadOnly(
                true).setListItems(listItems).build();

        // Required properties
        assertThat(readOnlyCombobox.getWidgetType()).isEqualTo(FormWidgetInfo.WIDGET_TYPE_COMBOBOX);
        assertThat(readOnlyCombobox.getWidgetIndex()).isEqualTo(15);
        assertThat(readOnlyCombobox.getWidgetRect()).isEqualTo(widgetRect);
        assertThat(readOnlyCombobox.getTextValue()).isEqualTo(textValue);
        assertThat(readOnlyCombobox.getAccessibilityLabel()).isEqualTo(a11yLabel);
        assertThat(readOnlyCombobox.getListItems()).isEqualTo(listItems);

        assertThat(readOnlyCombobox.isReadOnly()).isTrue();

        // Default properties
        assertThat(readOnlyCombobox.isEditableText()).isFalse();
        assertThat(readOnlyCombobox.getFontSize()).isEqualTo(0f);

        // Inapplicable properties
        assertThat(readOnlyCombobox.isMultiSelect()).isFalse();
        assertThat(readOnlyCombobox.isMultiLineText()).isFalse();
        assertThat(readOnlyCombobox.getMaxLength()).isEqualTo(-1);
    }

    @Test
    public void testCombobox_withEditableText() {
        List<ListItem> listItems = List.of(new ListItem("England", /* selected= */ true),
                new ListItem("Scotland", /* selected= */ true),
                new ListItem("Northern Ireland", /* selected= */ true),
                new ListItem("Wales", /* selected= */ false));
        Rect widgetRect = new Rect(450, 225, 650, 425);
        String textValue = "Northern Ireland";
        String a11yLabel = "Combobox editable";
        FormWidgetInfo editableCombobox = new FormWidgetInfo.Builder(
                /* widgetType= */ FormWidgetInfo.WIDGET_TYPE_COMBOBOX,
                /* widgetIndex= */ 5, widgetRect, textValue, a11yLabel).setEditableText(
                true).setListItems(listItems).build();

        // Required properties
        assertThat(editableCombobox.getWidgetType()).isEqualTo(FormWidgetInfo.WIDGET_TYPE_COMBOBOX);
        assertThat(editableCombobox.getWidgetIndex()).isEqualTo(5);
        assertThat(editableCombobox.getWidgetRect()).isEqualTo(widgetRect);
        assertThat(editableCombobox.getTextValue()).isEqualTo(textValue);
        assertThat(editableCombobox.getAccessibilityLabel()).isEqualTo(a11yLabel);
        assertThat(editableCombobox.getListItems()).isEqualTo(listItems);

        assertThat(editableCombobox.isEditableText()).isTrue();

        // Default properties
        assertThat(editableCombobox.isReadOnly()).isFalse();
        assertThat(editableCombobox.getFontSize()).isEqualTo(0f);

        // Inapplicable properties
        assertThat(editableCombobox.isMultiSelect()).isFalse();
        assertThat(editableCombobox.isMultiLineText()).isFalse();
        assertThat(editableCombobox.getMaxLength()).isEqualTo(-1);
    }

    @Test
    public void testCombobox_withFontSize() {
        List<ListItem> listItems = List.of(new ListItem("England", /* selected= */ true),
                new ListItem("Scotland", /* selected= */ true),
                new ListItem("Northern Ireland", /* selected= */ true),
                new ListItem("Wales", /* selected= */ false));
        Rect widgetRect = new Rect(220, 220, 520, 520);
        String textValue = "Northern Ireland";
        String a11yLabel = "Combobox editable";
        FormWidgetInfo editableCombobox = new FormWidgetInfo.Builder(
                /* widgetType= */ FormWidgetInfo.WIDGET_TYPE_COMBOBOX,
                /* widgetIndex= */ 8, widgetRect, textValue, a11yLabel).setEditableText(
                true).setFontSize(14f).setListItems(listItems).build();

        // Required properties
        assertThat(editableCombobox.getWidgetType()).isEqualTo(FormWidgetInfo.WIDGET_TYPE_COMBOBOX);
        assertThat(editableCombobox.getWidgetIndex()).isEqualTo(8);
        assertThat(editableCombobox.getWidgetRect()).isEqualTo(widgetRect);
        assertThat(editableCombobox.getTextValue()).isEqualTo(textValue);
        assertThat(editableCombobox.getAccessibilityLabel()).isEqualTo(a11yLabel);
        assertThat(editableCombobox.getListItems()).isEqualTo(listItems);

        assertThat(editableCombobox.isEditableText()).isTrue();
        assertThat(editableCombobox.getFontSize()).isEqualTo(14f);

        // Default properties
        assertThat(editableCombobox.isReadOnly()).isFalse();

        // Inapplicable properties
        assertThat(editableCombobox.isMultiSelect()).isFalse();
        assertThat(editableCombobox.isMultiLineText()).isFalse();
        assertThat(editableCombobox.getMaxLength()).isEqualTo(-1);
    }

    @Test
    public void testListbox() {
        List<ListItem> listItems = List.of(new ListItem("Apple", /* selected= */ false),
                new ListItem("Banana", /* selected= */ true),
                new ListItem("Pear", /* selected= */ false),
                new ListItem("Strawberry", /* selected= */ false),
                new ListItem("Jackfruit", /* selected= */ false));
        Rect widgetRect = new Rect(111, 222, 333, 444);
        String textValue = "Banana";
        String a11yLabel = "Listbox single select";
        FormWidgetInfo comboBox = new FormWidgetInfo.Builder(
                /* widgetType= */ FormWidgetInfo.WIDGET_TYPE_LISTBOX,
                /* widgetIndex= */ 4, widgetRect, textValue, a11yLabel).setListItems(
                listItems).build();

        // Required properties
        assertThat(comboBox.getWidgetType()).isEqualTo(FormWidgetInfo.WIDGET_TYPE_LISTBOX);
        assertThat(comboBox.getWidgetIndex()).isEqualTo(4);
        assertThat(comboBox.getWidgetRect()).isEqualTo(widgetRect);
        assertThat(comboBox.getTextValue()).isEqualTo(textValue);
        assertThat(comboBox.getAccessibilityLabel()).isEqualTo(a11yLabel);
        assertThat(comboBox.getListItems()).isEqualTo(listItems);

        // Default properties
        assertThat(comboBox.isReadOnly()).isFalse();
        assertThat(comboBox.isMultiSelect()).isFalse();

        // Inapplicable properties
        assertThat(comboBox.isEditableText()).isFalse();
        assertThat(comboBox.getFontSize()).isEqualTo(0f);
        assertThat(comboBox.isMultiLineText()).isFalse();
        assertThat(comboBox.getMaxLength()).isEqualTo(-1);
    }

    @Test
    public void testListbox_readOnly() {
        List<ListItem> listItems = List.of(new ListItem("One", /* selected= */ false),
                new ListItem("Two", /* selected= */ false),
                new ListItem("Three", /* selected= */ true),
                new ListItem("Four", /* selected= */ false),
                new ListItem("Fifty", /* selected= */ false));
        Rect widgetRect = new Rect(0, 0, 250, 100);
        String textValue = "Three";
        String a11yLabel = "Listbox single select";
        FormWidgetInfo readOnlyListbox = new FormWidgetInfo.Builder(
                /* widgetType= */ FormWidgetInfo.WIDGET_TYPE_LISTBOX,
                /* widgetIndex= */ 8, widgetRect, textValue, a11yLabel).setReadOnly(
                true).setListItems(listItems).build();

        // Required properties
        assertThat(readOnlyListbox.getWidgetType()).isEqualTo(FormWidgetInfo.WIDGET_TYPE_LISTBOX);
        assertThat(readOnlyListbox.getWidgetIndex()).isEqualTo(8);
        assertThat(readOnlyListbox.getWidgetRect()).isEqualTo(widgetRect);
        assertThat(readOnlyListbox.getTextValue()).isEqualTo(textValue);
        assertThat(readOnlyListbox.getAccessibilityLabel()).isEqualTo(a11yLabel);
        assertThat(readOnlyListbox.getListItems()).isEqualTo(listItems);

        assertThat(readOnlyListbox.isReadOnly()).isTrue();

        // Default properties
        assertThat(readOnlyListbox.isMultiSelect()).isFalse();

        // Inapplicable properties
        assertThat(readOnlyListbox.isEditableText()).isFalse();
        assertThat(readOnlyListbox.getFontSize()).isEqualTo(0f);
        assertThat(readOnlyListbox.isMultiLineText()).isFalse();
        assertThat(readOnlyListbox.getMaxLength()).isEqualTo(-1);
    }

    @Test
    public void testListbox_multiSelect() {
        List<ListItem> listItems = List.of(new ListItem("Archit", /* selected= */ false),
                new ListItem("Daniel", /* selected= */ true),
                new ListItem("Julia", /* selected= */ false),
                new ListItem("Hank", /* selected= */ false),
                new ListItem("Sandra", /* selected= */ true));
        Rect widgetRect = new Rect(100, 500, 150, 550);
        String textValue = "Daniel, Sandra";
        String a11yLabel = "Listbox multi select";
        FormWidgetInfo readOnlyListbox = new FormWidgetInfo.Builder(
                /* widgetType= */ FormWidgetInfo.WIDGET_TYPE_LISTBOX,
                /* widgetIndex= */ 3, widgetRect, textValue, a11yLabel).setMultiSelect(
                true).setListItems(listItems).build();

        // Required properties
        assertThat(readOnlyListbox.getWidgetType()).isEqualTo(FormWidgetInfo.WIDGET_TYPE_LISTBOX);
        assertThat(readOnlyListbox.getWidgetIndex()).isEqualTo(3);
        assertThat(readOnlyListbox.getWidgetRect()).isEqualTo(widgetRect);
        assertThat(readOnlyListbox.getTextValue()).isEqualTo(textValue);
        assertThat(readOnlyListbox.getAccessibilityLabel()).isEqualTo(a11yLabel);
        assertThat(readOnlyListbox.getListItems()).isEqualTo(listItems);

        assertThat(readOnlyListbox.isMultiSelect()).isTrue();

        // Default properties
        assertThat(readOnlyListbox.isReadOnly()).isFalse();

        // Inapplicable properties
        assertThat(readOnlyListbox.isEditableText()).isFalse();
        assertThat(readOnlyListbox.getFontSize()).isEqualTo(0f);
        assertThat(readOnlyListbox.isMultiLineText()).isFalse();
        assertThat(readOnlyListbox.getMaxLength()).isEqualTo(-1);
    }

    @Test
    public void testTextField() {
        Rect widgetRect = new Rect(0, 0, 100, 100);
        String textValue = "Chameleon";
        String a11yLabel = "Text field";
        FormWidgetInfo textField = new FormWidgetInfo.Builder(
                /* widgetType= */ FormWidgetInfo.WIDGET_TYPE_TEXTFIELD,
                /* widgetIndex= */ 3, widgetRect, textValue, a11yLabel).build();

        // Required properties
        assertThat(textField.getWidgetType()).isEqualTo(FormWidgetInfo.WIDGET_TYPE_TEXTFIELD);
        assertThat(textField.getWidgetIndex()).isEqualTo(3);
        assertThat(textField.getWidgetRect()).isEqualTo(widgetRect);
        assertThat(textField.getTextValue()).isEqualTo(textValue);
        assertThat(textField.getAccessibilityLabel()).isEqualTo(a11yLabel);

        // Default properties
        assertThat(textField.isEditableText()).isFalse();
        assertThat(textField.getFontSize()).isEqualTo(0f);
        assertThat(textField.isMultiLineText()).isFalse();
        assertThat(textField.getMaxLength()).isEqualTo(-1);
        assertThat(textField.isReadOnly()).isFalse();

        // Inapplicable properties
        assertThat(textField.isMultiSelect()).isFalse();
        assertThat(textField.getListItems()).isEmpty();
    }

    @Test
    public void testTextField_readOnly() {
        Rect widgetRect = new Rect(350, 560, 400, 590);
        String textValue = "Gecko";
        String a11yLabel = "Read only text field";
        FormWidgetInfo textField = new FormWidgetInfo.Builder(
                /* widgetType= */ FormWidgetInfo.WIDGET_TYPE_TEXTFIELD,
                /* widgetIndex= */ 5, widgetRect, textValue, a11yLabel).setReadOnly(true).build();

        // Required properties
        assertThat(textField.getWidgetType()).isEqualTo(FormWidgetInfo.WIDGET_TYPE_TEXTFIELD);
        assertThat(textField.getWidgetIndex()).isEqualTo(5);
        assertThat(textField.getWidgetRect()).isEqualTo(widgetRect);
        assertThat(textField.getTextValue()).isEqualTo(textValue);
        assertThat(textField.getAccessibilityLabel()).isEqualTo(a11yLabel);

        assertThat(textField.isReadOnly()).isTrue();

        // Default properties
        assertThat(textField.isEditableText()).isFalse();
        assertThat(textField.getFontSize()).isEqualTo(0f);
        assertThat(textField.isMultiLineText()).isFalse();
        assertThat(textField.getMaxLength()).isEqualTo(-1);

        // Inapplicable properties
        assertThat(textField.isMultiSelect()).isFalse();
        assertThat(textField.getListItems()).isEmpty();
    }

    @Test
    public void testTextField_withEditableText() {
        Rect widgetRect = new Rect(100, 100, 800, 800);
        String textValue = "Iguana";
        String a11yLabel = "Editable text field";
        FormWidgetInfo textField = new FormWidgetInfo.Builder(
                /* widgetType= */ FormWidgetInfo.WIDGET_TYPE_TEXTFIELD,
                /* widgetIndex= */ 7, widgetRect, textValue, a11yLabel).setEditableText(
                true).build();

        // Required properties
        assertThat(textField.getWidgetType()).isEqualTo(FormWidgetInfo.WIDGET_TYPE_TEXTFIELD);
        assertThat(textField.getWidgetIndex()).isEqualTo(7);
        assertThat(textField.getWidgetRect()).isEqualTo(widgetRect);
        assertThat(textField.getTextValue()).isEqualTo(textValue);
        assertThat(textField.getAccessibilityLabel()).isEqualTo(a11yLabel);

        assertThat(textField.isEditableText()).isTrue();

        // Default properties
        assertThat(textField.getFontSize()).isEqualTo(0f);
        assertThat(textField.isMultiLineText()).isFalse();
        assertThat(textField.getMaxLength()).isEqualTo(-1);
        assertThat(textField.isReadOnly()).isFalse();

        // Inapplicable properties
        assertThat(textField.isMultiSelect()).isFalse();
        assertThat(textField.getListItems()).isEmpty();
    }

    @Test
    public void testTextField_multiLine() {
        Rect widgetRect = new Rect(1500, 1650, 1650, 2000);
        String textValue = "Chameleon\nGecko\nIguana";
        String a11yLabel = "Text field";
        FormWidgetInfo textField = new FormWidgetInfo.Builder(
                /* widgetType= */ FormWidgetInfo.WIDGET_TYPE_TEXTFIELD,
                /* widgetIndex= */ 0, widgetRect, textValue, a11yLabel).setMultiLineText(
                true).build();

        // Required properties
        assertThat(textField.getWidgetType()).isEqualTo(FormWidgetInfo.WIDGET_TYPE_TEXTFIELD);
        assertThat(textField.getWidgetIndex()).isEqualTo(0);
        assertThat(textField.getWidgetRect()).isEqualTo(widgetRect);
        assertThat(textField.getTextValue()).isEqualTo(textValue);
        assertThat(textField.getAccessibilityLabel()).isEqualTo(a11yLabel);

        assertThat(textField.isMultiLineText()).isTrue();

        // Default properties
        assertThat(textField.isEditableText()).isFalse();
        assertThat(textField.getFontSize()).isEqualTo(0f);
        assertThat(textField.getMaxLength()).isEqualTo(-1);
        assertThat(textField.isReadOnly()).isFalse();

        // Inapplicable properties
        assertThat(textField.isMultiSelect()).isFalse();
        assertThat(textField.getListItems()).isEmpty();
    }

    @Test
    public void testTextField_withMaxLength() {
        Rect widgetRect = new Rect(600, 500, 700, 600);
        String textValue = "Dragon";
        String a11yLabel = "Text field with maximum length";
        FormWidgetInfo textField = new FormWidgetInfo.Builder(
                /* widgetType= */ FormWidgetInfo.WIDGET_TYPE_TEXTFIELD,
                /* widgetIndex= */ 2, widgetRect, textValue, a11yLabel).setMaxLength(10).build();

        // Required properties
        assertThat(textField.getWidgetType()).isEqualTo(FormWidgetInfo.WIDGET_TYPE_TEXTFIELD);
        assertThat(textField.getWidgetIndex()).isEqualTo(2);
        assertThat(textField.getWidgetRect()).isEqualTo(widgetRect);
        assertThat(textField.getTextValue()).isEqualTo(textValue);
        assertThat(textField.getAccessibilityLabel()).isEqualTo(a11yLabel);

        assertThat(textField.getMaxLength()).isEqualTo(10);

        // Default properties
        assertThat(textField.isEditableText()).isFalse();
        assertThat(textField.getFontSize()).isEqualTo(0f);
        assertThat(textField.isMultiLineText()).isFalse();
        assertThat(textField.isReadOnly()).isFalse();

        // Inapplicable properties
        assertThat(textField.isMultiSelect()).isFalse();
        assertThat(textField.getListItems()).isEmpty();
    }

    @Test
    public void testTextField_fontSize() {
        Rect widgetRect = new Rect(1000, 1000, 1500, 1500);
        String textValue = "Snake";
        String a11yLabel = "Text field with font size";
        FormWidgetInfo textField = new FormWidgetInfo.Builder(
                /* widgetType= */ FormWidgetInfo.WIDGET_TYPE_TEXTFIELD,
                /* widgetIndex= */ 22, widgetRect, textValue, a11yLabel).setFontSize(16f).build();

        // Required properties
        assertThat(textField.getWidgetType()).isEqualTo(FormWidgetInfo.WIDGET_TYPE_TEXTFIELD);
        assertThat(textField.getWidgetIndex()).isEqualTo(22);
        assertThat(textField.getWidgetRect()).isEqualTo(widgetRect);
        assertThat(textField.getTextValue()).isEqualTo(textValue);
        assertThat(textField.getAccessibilityLabel()).isEqualTo(a11yLabel);

        assertThat(textField.getFontSize()).isEqualTo(16f);

        // Default properties
        assertThat(textField.isEditableText()).isFalse();
        assertThat(textField.isMultiLineText()).isFalse();
        assertThat(textField.getMaxLength()).isEqualTo(-1);
        assertThat(textField.isReadOnly()).isFalse();

        // Inapplicable properties
        assertThat(textField.isMultiSelect()).isFalse();
        assertThat(textField.getListItems()).isEmpty();
    }

    @Test
    public void testSignature() {
        Rect widgetRect = new Rect(62, 114, 212, 376);
        String textValue = "John Doe";
        String a11yLabel = "Signature";
        FormWidgetInfo signature = new FormWidgetInfo.Builder(
                /* widgetType= */ FormWidgetInfo.WIDGET_TYPE_SIGNATURE,
                /* widgetIndex= */ 4, widgetRect, textValue, a11yLabel).build();

        // Required properties
        assertThat(signature.getWidgetType()).isEqualTo(FormWidgetInfo.WIDGET_TYPE_SIGNATURE);
        assertThat(signature.getWidgetIndex()).isEqualTo(4);
        assertThat(signature.getWidgetRect()).isEqualTo(widgetRect);
        assertThat(signature.getTextValue()).isEqualTo(textValue);
        assertThat(signature.getAccessibilityLabel()).isEqualTo(a11yLabel);

        // Default properties
        assertThat(signature.isReadOnly()).isFalse();

        // Inapplicable properties
        assertThat(signature.isEditableText()).isFalse();
        assertThat(signature.isMultiSelect()).isFalse();
        assertThat(signature.isMultiLineText()).isFalse();
        assertThat(signature.getMaxLength()).isEqualTo(-1);
        assertThat(signature.getFontSize()).isEqualTo(0f);
        assertThat(signature.getListItems()).isEmpty();
    }

    @Test
    public void testSignature_readOnly() {
        Rect widgetRect = new Rect(214, 0, 468, 112);
        String textValue = "Jane Doe";
        String a11yLabel = "Signature Read Only";
        FormWidgetInfo readOnlySignature = new FormWidgetInfo.Builder(
                /* widgetType= */ FormWidgetInfo.WIDGET_TYPE_SIGNATURE,
                /* widgetIndex= */ 4, widgetRect, textValue, a11yLabel).setReadOnly(true).build();

        // Required properties
        assertThat(readOnlySignature.getWidgetType()).isEqualTo(
                FormWidgetInfo.WIDGET_TYPE_SIGNATURE);
        assertThat(readOnlySignature.getWidgetIndex()).isEqualTo(4);
        assertThat(readOnlySignature.getWidgetRect()).isEqualTo(widgetRect);
        assertThat(readOnlySignature.getTextValue()).isEqualTo(textValue);
        assertThat(readOnlySignature.getAccessibilityLabel()).isEqualTo(a11yLabel);

        // Default properties
        assertThat(readOnlySignature.isReadOnly()).isTrue();

        // Inapplicable properties
        assertThat(readOnlySignature.isEditableText()).isFalse();
        assertThat(readOnlySignature.isMultiSelect()).isFalse();
        assertThat(readOnlySignature.isMultiLineText()).isFalse();
        assertThat(readOnlySignature.getMaxLength()).isEqualTo(-1);
        assertThat(readOnlySignature.getFontSize()).isEqualTo(0f);
        assertThat(readOnlySignature.getListItems()).isEmpty();
    }

    @Test
    public void testParcelable_clickType() {
        Rect widgetRect = new Rect(100, 0, 150, 50);
        String textValue = "Reset";
        String a11yLabel = "Reset button";
        FormWidgetInfo in = new FormWidgetInfo.Builder(
                /* widgetType= */ FormWidgetInfo.WIDGET_TYPE_PUSHBUTTON,
                /* widgetIndex= */ 4, widgetRect, textValue, a11yLabel).build();

        Parcel parcel = Parcel.obtain();
        in.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        FormWidgetInfo out = FormWidgetInfo.CREATOR.createFromParcel(parcel);

        assertEquals(in, out);
    }

    @Test
    public void testParcelable_choiceType() {
        List<ListItem> listItems = List.of(new ListItem("The", /* selected= */ false),
                new ListItem("quick", /* selected= */ true),
                new ListItem("brown", /* selected= */ false),
                new ListItem("fox", /* selected= */ false),
                new ListItem("jumped", /* selected= */ false),
                new ListItem("over", /* selected= */ false),
                new ListItem("the", /* selected= */ false),
                new ListItem("lazy", /* selected= */ false),
                new ListItem("dog", /* selected= */ false));
        Rect widgetRect = new Rect(1250, 0, 1500, 250);
        String textValue = "quick";
        String a11yLabel = "Combobox single select";
        FormWidgetInfo in = new FormWidgetInfo.Builder(
                /* widgetType= */ FormWidgetInfo.WIDGET_TYPE_COMBOBOX,
                /* widgetIndex= */ 1, widgetRect, textValue, a11yLabel).setListItems(
                listItems).build();

        Parcel parcel = Parcel.obtain();
        in.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        FormWidgetInfo out = FormWidgetInfo.CREATOR.createFromParcel(parcel);

        assertEquals(in, out);
    }

    @Test
    public void testParcelable_textType() {
        Rect widgetRect = new Rect(1000, 1000, 1500, 1500);
        String textValue = "Snake";
        String a11yLabel = "Text field with font size";
        FormWidgetInfo in = new FormWidgetInfo.Builder(
                /* widgetType= */ FormWidgetInfo.WIDGET_TYPE_TEXTFIELD,
                /* widgetIndex= */ 22, widgetRect, textValue, a11yLabel).setFontSize(16f).build();

        Parcel parcel = Parcel.obtain();
        in.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        FormWidgetInfo out = FormWidgetInfo.CREATOR.createFromParcel(parcel);

        assertEquals(in, out);
    }

    @Test
    public void creator_newArray() {
        FormWidgetInfo[] formWidgetInfos = FormWidgetInfo.CREATOR.newArray(20);

        assertEquals(formWidgetInfos.length, 20);
    }

    @Test
    public void describeContents() {
        Rect widgetRect = new Rect(1000, 1000, 1500, 1500);
        String textValue = "Snake";
        String a11yLabel = "Text field with font size";
        FormWidgetInfo formWidgetInfo = new FormWidgetInfo.Builder(
                /* widgetType= */ FormWidgetInfo.WIDGET_TYPE_TEXTFIELD,
                /* widgetIndex= */ 22, widgetRect, textValue, a11yLabel).setFontSize(16f).build();

        assertEquals(0, formWidgetInfo.describeContents());
    }


    @Test
    public void testEqualsHashCode_clickType_matchingProperties() {
        Rect widgetRect = new Rect(100, 0, 150, 50);
        String textValue = "Reset";
        String a11yLabel = "Reset button";
        FormWidgetInfo one = new FormWidgetInfo.Builder(
                /* widgetType= */ FormWidgetInfo.WIDGET_TYPE_PUSHBUTTON,
                /* widgetIndex= */ 4, widgetRect, textValue, a11yLabel).build();
        FormWidgetInfo two = new FormWidgetInfo.Builder(
                /* widgetType= */ FormWidgetInfo.WIDGET_TYPE_PUSHBUTTON,
                /* widgetIndex= */ 4, widgetRect, textValue, a11yLabel).build();

        assertEquals(one, two);
        assertEquals(one.hashCode(), two.hashCode());
    }

    @Test
    public void testEqualsHashCode_clickType_notMatching() {
        Rect widgetRect = new Rect(100, 0, 150, 50);
        String a11yLabel = "Reset button";
        FormWidgetInfo one = new FormWidgetInfo.Builder(
                /* widgetType= */ FormWidgetInfo.WIDGET_TYPE_PUSHBUTTON,
                /* widgetIndex= */ 4, widgetRect, "I am jogging", a11yLabel).build();
        FormWidgetInfo two = new FormWidgetInfo.Builder(
                /* widgetType= */ FormWidgetInfo.WIDGET_TYPE_PUSHBUTTON,
                /* widgetIndex= */ 4, widgetRect, "The dog is big!", a11yLabel).build();

        assertNotEquals(one, two);
        assertNotEquals(one.hashCode(), two.hashCode());
    }

    @Test
    public void testEqualsHashCode_choiceType_matchingProperties() {
        List<ListItem> listItems = List.of(new ListItem("The", /* selected= */ false),
                new ListItem("quick", /* selected= */ true),
                new ListItem("brown", /* selected= */ false),
                new ListItem("fox", /* selected= */ false),
                new ListItem("jumped", /* selected= */ false),
                new ListItem("over", /* selected= */ false),
                new ListItem("the", /* selected= */ false),
                new ListItem("lazy", /* selected= */ false),
                new ListItem("dog", /* selected= */ false));
        Rect widgetRect = new Rect(1250, 0, 1500, 250);
        String textValue = "quick";
        String a11yLabel = "Combobox single select";
        FormWidgetInfo one = new FormWidgetInfo.Builder(
                /* widgetType= */ FormWidgetInfo.WIDGET_TYPE_COMBOBOX,
                /* widgetIndex= */ 1, widgetRect, textValue, a11yLabel).setListItems(
                listItems).build();
        FormWidgetInfo two = new FormWidgetInfo.Builder(
                /* widgetType= */ FormWidgetInfo.WIDGET_TYPE_COMBOBOX,
                /* widgetIndex= */ 1, widgetRect, textValue, a11yLabel).setListItems(
                listItems).build();

        assertEquals(one, two);
        assertEquals(one.hashCode(), two.hashCode());
    }

    @Test
    public void testEqualsHashCode_choiceType_notMatching() {
        List<ListItem> listItems = List.of(new ListItem("The", /* selected= */ false),
                new ListItem("quick", /* selected= */ true),
                new ListItem("brown", /* selected= */ false),
                new ListItem("fox", /* selected= */ false),
                new ListItem("jumped", /* selected= */ false),
                new ListItem("over", /* selected= */ false),
                new ListItem("the", /* selected= */ false),
                new ListItem("lazy", /* selected= */ false),
                new ListItem("dog", /* selected= */ false));
        Rect widgetRect = new Rect(1250, 0, 1500, 250);
        String textValue = "quick";
        String a11yLabel = "Combobox single select";
        FormWidgetInfo one = new FormWidgetInfo.Builder(
                /* widgetType= */ FormWidgetInfo.WIDGET_TYPE_COMBOBOX,
                /* widgetIndex= */ 1, widgetRect, textValue, a11yLabel).setListItems(
                listItems).build();
        FormWidgetInfo two = new FormWidgetInfo.Builder(
                /* widgetType= */ FormWidgetInfo.WIDGET_TYPE_COMBOBOX,
                /* widgetIndex= */ 1, widgetRect, textValue, a11yLabel).setListItems(
                listItems.subList(2, 4)).build();

        assertNotEquals(one, two);
        assertNotEquals(one.hashCode(), two.hashCode());
    }

    @Test
    public void testEqualsHashCode_textType_matchingProperties() {
        Rect widgetRect = new Rect(1000, 1000, 1500, 1500);
        String textValue = "Snake";
        String a11yLabel = "Text field with font size";
        FormWidgetInfo one = new FormWidgetInfo.Builder(
                /* widgetType= */ FormWidgetInfo.WIDGET_TYPE_TEXTFIELD,
                /* widgetIndex= */ 22, widgetRect, textValue, a11yLabel).setFontSize(16f).build();
        FormWidgetInfo two = new FormWidgetInfo.Builder(
                /* widgetType= */ FormWidgetInfo.WIDGET_TYPE_TEXTFIELD,
                /* widgetIndex= */ 22, widgetRect, textValue, a11yLabel).setFontSize(16f).build();

        assertEquals(one, two);
        assertEquals(one.hashCode(), two.hashCode());
    }

    @Test
    public void testEqualsHashCode_textType_notMatching() {
        Rect widgetRect = new Rect(1000, 1000, 1500, 1500);
        String textValue = "Snake";
        String a11yLabel = "Text field with font size";
        FormWidgetInfo one = new FormWidgetInfo.Builder(
                /* widgetType= */ FormWidgetInfo.WIDGET_TYPE_TEXTFIELD,
                /* widgetIndex= */ 22, widgetRect, textValue, a11yLabel).setFontSize(100f).build();
        FormWidgetInfo two = new FormWidgetInfo.Builder(
                /* widgetType= */ FormWidgetInfo.WIDGET_TYPE_TEXTFIELD,
                /* widgetIndex= */ 22, widgetRect, textValue, a11yLabel).setFontSize(16f).build();

        assertNotEquals(one, two);
        assertNotEquals(one.hashCode(), two.hashCode());
    }

    @Test
    public void testEquals_notFormWidgetInfo() {
        Rect widgetRect = new Rect(1000, 1000, 1500, 1500);
        String textValue = "Snake";
        String a11yLabel = "Text field with font size";
        FormWidgetInfo formWidgetInfo = new FormWidgetInfo.Builder(
                /* widgetType= */ FormWidgetInfo.WIDGET_TYPE_TEXTFIELD,
                /* widgetIndex= */ 22, widgetRect, textValue, a11yLabel).setFontSize(100f).build();

        assertNotEquals(formWidgetInfo, new Object());
    }

    @Test
    public void testBuilder_setEditableTextUnsupported() {
        assertThrows(IllegalArgumentException.class, () -> {
            new FormWidgetInfo.Builder(
                    /* widgetType= */ FormWidgetInfo.WIDGET_TYPE_PUSHBUTTON,
                    /* widgetIndex= */ 9, new Rect(0, 0, 100, 100), "Reset",
                    "Reset button").setEditableText(true);
        });
    }

    @Test
    public void testBuilder_setMultiSelectOnNonListbox() {
        assertThrows(IllegalArgumentException.class, () -> {
            new FormWidgetInfo.Builder(
                    /* widgetType= */ FormWidgetInfo.WIDGET_TYPE_COMBOBOX,
                    /* widgetIndex= */ 1, new Rect(500, 450, 600, 500), "Selected",
                    "Combobox").setMultiSelect(true);
        });
    }

    @Test
    public void testBuilder_setMultilineTextOnNonTextField() {
        assertThrows(IllegalArgumentException.class, () -> {
            new FormWidgetInfo.Builder(
                    /* widgetType= */ FormWidgetInfo.WIDGET_TYPE_RADIOBUTTON,
                    /* widgetIndex= */ 0, new Rect(10, 10, 50, 50), "False",
                    "Radio button A").setMultiLineText(true);
        });
    }

    @Test
    public void testBuilder_setMaxLengthOnNonTextField() {
        assertThrows(IllegalArgumentException.class, () -> {
            new FormWidgetInfo.Builder(
                    /* widgetType= */ FormWidgetInfo.WIDGET_TYPE_CHECKBOX,
                    /* widgetIndex= */ 15, new Rect(1000, 1000, 5000, 5000), "False",
                    "Check box").setMaxLength(50);
        });
    }

    @Test
    public void testBuilder_setInvalidMaxLength() {
        assertThrows(IllegalArgumentException.class, () -> {
            new FormWidgetInfo.Builder(
                    /* widgetType= */ FormWidgetInfo.WIDGET_TYPE_TEXTFIELD,
                    /* widgetIndex= */ 1, new Rect(400, 200, 500, 300), "Salamander",
                    "Text input").setMaxLength(-56);
        });
    }

    @Test
    public void testBuilder_setFontSizeUnsupported() {
        assertThrows(IllegalArgumentException.class, () -> {
            new FormWidgetInfo.Builder(
                    /* widgetType= */ FormWidgetInfo.WIDGET_TYPE_CHECKBOX,
                    /* widgetIndex= */ 4, new Rect(200, 200, 300, 250), "False",
                    "Check box").setFontSize(10f);
        });
    }

    @Test
    public void testBuilder_setInvalidFontSize() {
        assertThrows(IllegalArgumentException.class, () -> {
            new FormWidgetInfo.Builder(
                    /* widgetType= */ FormWidgetInfo.WIDGET_TYPE_TEXTFIELD,
                    /* widgetIndex= */ 1, new Rect(240, 320, 300, 390), "Newt",
                    "Text input").setFontSize(-10f);
        });
    }

    @Test
    public void testBuilder_nullListItems() {
        assertThrows(NullPointerException.class, () -> {
            new FormWidgetInfo.Builder(
                    /* widgetType= */ FormWidgetInfo.WIDGET_TYPE_COMBOBOX,
                    /* widgetIndex= */ 10, new Rect(120, 640, 200, 700), "Selected",
                    "Combobox").setListItems(null);
        });
    }

    @Test
    public void testBuilder_setListItemsUnsupported() {
        assertThrows(IllegalArgumentException.class, () -> {
            new FormWidgetInfo.Builder(
                    /* widgetType= */ FormWidgetInfo.WIDGET_TYPE_TEXTFIELD,
                    /* widgetIndex= */ 8, new Rect(1, 2, 3, 4), "Tadpole",
                    "Text input").setListItems(
                    List.of(new ListItem("Choice option", /* selected= */ false)));
        });
    }
}
