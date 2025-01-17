# DU Doku

DU Doku is an Android application designed to support [Museum Wiesbaden](https://museum-wiesbaden.de) in their digitalization efforts by streamlining object documentation.

The app features a barcode scanner (powered by Google ML Kit) that detects the barcode of an object. Photos taken through the app after barcode detection are saved in the directory `DCIM/MuWiScan/` and named `barcode_ii.jpg`, where

- `barcode` represents the scanned barcode, and
- `ii` is a consecutive number for each photo.

This naming convention allows photos to be imported into the database in batches and automatically assign them to the correct database entry, if available.
Additionally, some databases support Exif metadata tags.
To facilitate this, the app includes an input field where staff members can enter their names, which are then stored in the metadata using the *Artist* tag.
The [Apache Commons Imaging](https://github.com/apache/commons-imaging) library ensures proper encoding, including support for umlauts.
