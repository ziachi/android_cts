# Approved Known Issues

This directory contains one text proto buffer file for each known issue approved for reporting by
`Build.getBackportedFixStatus`.

## Format

Known issue files are named `ki<id>.txtpb` containing the data for a BackportedFix defined in
[backported_fixes.proto](../backported_fixes.proto) like [ki350037023.txtpb]

```prototext
known_issue: 350037023
alias: 1
```

All approved known issues must get an owners approval by Android Partner Engineering as defined in
the [OWNERS] file.
