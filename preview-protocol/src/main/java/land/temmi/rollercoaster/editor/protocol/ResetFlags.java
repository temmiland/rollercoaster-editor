package land.temmi.rollercoaster.editor.protocol;

/**
 * Editor asks the preview to clear its test GameState's flags and variables, so a test run can
 * start from a known-empty state instead of whatever earlier triggers left behind. A marker
 * message with no fields, like ShowSampleLevel.
 */
public final class ResetFlags {
}
