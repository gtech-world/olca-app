#!/bin/bash

DIST="aicpLCA_macOS_x64_1.0.4_$(date '+%Y-%m-%d')"
BUNDLE_ID="org.aicplca.app"
APP_DMG="build/tmp/macosx.cocoa.x86_64/aicpLCA_dmg/aicpLCA.app"
APP_PKG="build/tmp/macosx.cocoa.x86_64/aicpLCA_pkg/aicpLCA.app"
APP_UNSIGNED="build/macosx.cocoa.x86_64/aicpLCA/aicpLCA.app"
DMG="build/dist/${DIST}.dmg"
PKG="build/dist/${DIST}.pkg"

# Image disk parameters
BACKGROUND_DMG="resources/background_dmg.png"
VOLUME_ICON_FILE="$APP_UNSIGNED/Contents/Resources/logo.icns"
VOLUME_NAME="aicpLCA Installer"


clean() {
  printf "Removing previous files... "
  rm -f jars.list libraries.list
  rm -rf tmp
  rm -f "$PRODUCT"
  rm -rf "${APP%/*.*}"
  printf " Done.\n"
}

cp_app() {
  printf "\nCopying the unsigned app... "
  mkdir -p "${APP%/*.*}"
  cp -r "$APP_UNSIGNED" "$APP"
  printf " Done.\n"
}

sign_lib() {
  printf "\nSigning the frameworks and libraries with codesign...\n"
  find "${APP}/" -depth \
   -name "*.framework" \
   -or -name "*.dylib" \
   -or -name "*.bundle" \
   -or -name "*.so" \
   -or -name "*.jnilib" |
    while read -r file;
    do
      codesign -f -v --entitlements "${APP}/Contents/aicpLCA.entitlements" \
        --timestamp --options runtime  -i "$BUNDLE_ID" -s "$APP_ID" "$file";
    done
}

sign_jar() {
  printf "\nSigning all the libraries contained into .jar files.\n"
  # Find all the .jar files in the application
  find "$APP" -depth -name "*.jar" > jars.list

  # Loop over all the .jar files
  while IFS="" read -r p || [ -n "$p" ]
    do
      printf "Checking %s...\n" "$p"

      # List all the libraries that need to be sign.
      jar tf "$p" | grep -E '.so$|.framework$|.dylib$|.bundle$|.jnilib$' \
       > libraries.list

      # Loop over the libraries of that JAR.
      while IFS="" read -r file || [ -n "$file" ]
        do
          printf "  Signing %s...\n  " "$file"
          # Extract the library
          mkdir tmp
          cd tmp || exit 0
          jar xf "../${p}" "$file"
          cd ..
          # Sign the library
          codesign -f -v --entitlements "${APP}/Contents/aicpLCA.entitlements" \
           --timestamp --options runtime  -i "$BUNDLE_ID" -s "$APP_ID" \
            "tmp/${file}"
          # Update the JAR with the signed library
          jar uf "$p" "tmp/${file}"
          rm -r tmp
        done < libraries.list

      rm -f libraries.list
    done < jars.list

    rm -f jars.list
}

build_pkg() {
  printf "\nCreating the package installer file...\n"
  productbuild --sign "$STORE_INSTALLER_ID" --component "$APP" /Applications \
   "$PKG"
}

build_dmg() {
  NOTARIZATION_DMG="${APP%/*.*}/notarization.dmg"
  rm -f "$NOTARIZATION_DMG"

  printf "\nCreating the disk image installer file to be notarized...\n"
  hdiutil create -srcfolder "${APP%/*.*}/" \
   -volname "$(basename "NOTARIZATION_DMG")" -fs "HFS+" "$NOTARIZATION_DMG"

  printf "\nNotarization of the DMG...\n"
  xcrun notarytool submit "$NOTARIZATION_DMG" --keychain-profile "$KEYCHAIN" \
   --wait

  printf "\nStapling the app...\n"
  xcrun stapler staple "$APP"

  printf "\nCreating the disk image installer file to be distributed...\n"
  create-dmg \
   --volname "$VOLUME_NAME" \
   --background "$BACKGROUND_DMG" \
   --volicon "$VOLUME_ICON_FILE" \
   --window-pos 200 120 --window-size 800 400 --icon-size 150 \
   --icon "$(basename "$APP")" 200 160 \
   --app-drop-link 600 155 \
   "$DMG" "$APP"

  printf "\n If notarization failed, see the details by running: "
  printf "\n xcrun notarytool log  --keychain-profile <name> <REQUEST_ID>"
}

notarize() {
  if [ "$1" = "pkg" ]; then
    APP="$APP_PKG"
    PRODUCT="$PKG"
    APP_ID="$STORE_APP_ID"
  elif [ "$1" = "dmg" ]; then
    APP="$APP_DMG"
    PRODUCT="$DMG"
    APP_ID="$DEV_APP_ID"
  fi

  clean
  cp_app

  printf "\nConverting the XML files to the right format...\n"
  plutil -convert xml1 "${APP}/Contents/aicpLCA.entitlements"
  plutil -convert xml1 "${APP}/Contents/Info.plist"

  printf "\nRemoving eventual quarantine attribute...\n"
  xattr -d com.apple.quarantine "$APP"

  sign_lib
  sign_jar

  printf "\nSigning the aicpLCA executable at runtime...\n"
  codesign -f -v --deep --entitlements "${APP}/Contents/aicpLCA.entitlements" \
    --timestamp --options runtime -i "$BUNDLE_ID" -s "$APP_ID" \
    "${APP}/Contents/MacOS/aicpLCA"

  printf "\nSigning the app bundle with the certificate...\n"
  codesign -f -v --entitlements "${APP}/Contents/aicpLCA.entitlements" \
    --timestamp --options runtime  -i "$BUNDLE_ID" -s "$APP_ID" "$APP"
  printf "\nChecking signature of the bundle...\n"
  codesign -dvv "$APP"

  if [ "$1" = "pkg" ]; then
    build_pkg
  elif [ "$1" = "dmg" ]; then
    build_dmg
  fi

  printf "\nEnd of building of %s. Please test before distributing.\n" "$PRODUCT"
}

upload_pkg() {
  printf "\nValidating and uploading the app to the App Store...\n"
  xcrun altool --validate-app -f "$PKG" -t osx -u andreas.ciroth@web.de \
    -p @keychain:"$KEYCHAIN"

  xcrun altool --upload-app -f "$ZIP" -t osx -u andreas.ciroth@web.de \
    -p @keychain:"$KEYCHAIN"
}

usage() {
	cat <<EOHELP

Create Mac distributions for aicpLCA.

Usage:  $0 [args] [<pkg>|<dmg>|<upload>]

  --keychain <password name>
    Apple password stored in Keychain Access.

<pkg> sign the code to create a .pkg installer, validate and upload the app to
 the App Store.
  --store-id-app <id>
    Apple ID to sign the application for the Apple Store ("3rd Party Mac
    Developer Application: GreenDelta GmbH (<code>)")
  --store-id-installer <id>
      Apple ID to sign the installer for the Apple Store ("3rd Party Mac
      Developer Installer: GreenDelta GmbH (<code>)")

<dmg> sign the code to create a .dmg disk image, notarize and staple the app.
  --dev-id-app <id>
      Apple ID to sign the application for the independent distribution
      ("Developer ID Application: GreenDelta GmbH (<code>)")

<upload> validate and upload the .pkg app to the App Store.

EOHELP
	exit 0
}

# Argument parsing
while [[ "${1:0:1}" = "-" ]]; do
	case $1 in
		--store-id-app)
			STORE_APP_ID="$2"
			shift; shift;;
		--store-id-installer)
			STORE_INSTALLER_ID="$2"
			shift; shift;;
		--dev-id-app)
		  DEV_APP_ID="$2"
      shift; shift;;
    --keychain)
      KEYCHAIN="$2"
      shift; shift;;
    --help | -h)
      usage;;
    -*)
      echo "Unknown argument: $1. Run $0 --help to see usage."
      exit 1;;
  esac
done

DEV_APP_ID="Developer ID Application: Fu Gavin (9835AZG46U)"
KEYCHAIN="notarytool-aicpLCA"
echo "type: $1"

if [ -z "$KEYCHAIN" ]; then
    echo "Missing keychain argument. Run $0 --help to see usage."
fi

if [ "$1" = "pkg" ]; then
  if [ -z "$STORE_APP_ID" ] || [ -z "$STORE_INSTALLER_ID" ]; then
    echo "Missing argument. Run $0 --help to see usage."
  fi
  notarize "pkg"
elif [ "$1" = "dmg" ]; then
  if [ -z "$DEV_APP_ID" ]; then
    echo "Missing argument. Run $0 --help to see usage."
  fi
  notarize "dmg"
elif [ -z "$1" ]; then
  usage
fi

# if [ "$1" = "pkg" ] || [ "$1" = "upload" ]; then
#   while true; do
#       read -rp "Do you wish to upload aicpLCA to the App Store? (Y/n)" answer
#       case $answer in
#           Y ) upload_pkg; break;;
#           n ) exit;;
#           * ) echo "Please answer Y or n.";;
#       esac
#   done
# fi
