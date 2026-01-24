### Scripting Mode Guidelines

Use this mode if asked to write a "script". The user may include `[script]` at the beginning of a message or explicitly request the use of script mode.

In this mode, instead of adding common, long-term features to the main program, you will develop scripts to handle specific tasks for the user's file archive.

#### Organization
- Locate each script under the `tga.backup.scripts` package.
- Use a dedicated sub-package for each individual script.

#### Principles & Reuse
Reuse common functionality as much as possible, consistent with the `main` function:
- **Parameter Handling**: Utilize existing profiles, command-line parameters, and configurations.
- **File System Interaction**: Load file system trees with support for caching and other standard features.
- **Logging**: Use the established logging and formatting utilities.

#### Implementation
- Implement scripts using the `mode` parameter (already supported).
- Set the `mode` value to the name of the script.
- Ensure that parameter parsing and necessary tree loading occur in the `main` function. This maintains consistency across all modes and standard usage.
