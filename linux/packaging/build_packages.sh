#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LINUX_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
ROOT_DIR="$(cd "$LINUX_DIR/.." && pwd)"
OUT_DIR="$ROOT_DIR/artifacts/linux"
BUILD_TMP="$(mktemp -d)"
trap 'rm -rf "$BUILD_TMP"' EXIT

VERSION="${1:-${VERSION:-2.2.1}}"
VERSION="${VERSION#v}"
mkdir -p "$OUT_DIR"

echo "[1/4] Building portable TAR.GZ archive..."
STAGE_TAR="$BUILD_TMP/DeskFlow-${VERSION}-linux-x86_64"
mkdir -p "$STAGE_TAR/bin" "$STAGE_TAR/src" "$STAGE_TAR/share/applications" "$STAGE_TAR/share/icons/hicolor/scalable/apps" "$STAGE_TAR/share/metainfo"
cp -a "$LINUX_DIR/bin/deskflow" "$STAGE_TAR/bin/deskflow"
chmod 0755 "$STAGE_TAR/bin/deskflow"
cp -a "$LINUX_DIR/src/deskflow" "$STAGE_TAR/src/"
cp -a "$LINUX_DIR/data/com.deskflow.Station.desktop" "$STAGE_TAR/share/applications/"
cp -a "$LINUX_DIR/data/com.deskflow.Station.metainfo.xml" "$STAGE_TAR/share/metainfo/"
cp -a "$ROOT_DIR/shared/assets/deskflow-icon.svg" "$STAGE_TAR/share/icons/hicolor/scalable/apps/com.deskflow.Station.svg"
tar -C "$BUILD_TMP" -czf "$OUT_DIR/DeskFlow-${VERSION}-linux-x86_64.tar.gz" "DeskFlow-${VERSION}-linux-x86_64"

echo "[2/4] Building Debian package (.deb)..."
DEB_WORK="$BUILD_TMP/deb"
DEB_ROOT="$DEB_WORK/root"
mkdir -p "$DEB_WORK/control" \
         "$DEB_ROOT/usr/bin" \
         "$DEB_ROOT/usr/share/deskflow/src" \
         "$DEB_ROOT/usr/share/applications" \
         "$DEB_ROOT/usr/share/metainfo" \
         "$DEB_ROOT/usr/share/icons/hicolor/scalable/apps"

cp -a "$LINUX_DIR/bin/deskflow" "$DEB_ROOT/usr/bin/deskflow"
chmod 0755 "$DEB_ROOT/usr/bin/deskflow"
cp -a "$LINUX_DIR/src/deskflow" "$DEB_ROOT/usr/share/deskflow/src/"
cp -a "$LINUX_DIR/data/com.deskflow.Station.desktop" "$DEB_ROOT/usr/share/applications/"
cp -a "$LINUX_DIR/data/com.deskflow.Station.metainfo.xml" "$DEB_ROOT/usr/share/metainfo/"
cp -a "$ROOT_DIR/shared/assets/deskflow-icon.svg" "$DEB_ROOT/usr/share/icons/hicolor/scalable/apps/com.deskflow.Station.svg"

cat > "$DEB_WORK/control/control" <<EOF
Package: deskflow
Version: ${VERSION}
Section: utils
Priority: optional
Architecture: amd64
Depends: python3 (>= 3.10), python3-gi, gir1.2-gtk-4.0, gir1.2-adw-1
Maintainer: Deskflow Engineering <engineering@deskflow.local>
Description: Deskflow Native Linux Control Center (GTK4 + Libadwaita)
 Native GNOME/GTK4 + Libadwaita desktop application for Deskflow Focus Mode,
 Sticky Notes, Exam Countdowns, Weather Forecasts, Morse Code, and ESP32 OLED.
EOF

echo "2.0" > "$DEB_WORK/debian-binary"
tar -C "$DEB_WORK/control" -czf "$DEB_WORK/control.tar.gz" .
tar -C "$DEB_ROOT" -czf "$DEB_WORK/data.tar.gz" .
rm -f "$OUT_DIR/deskflow_${VERSION}_amd64.deb"
(
  cd "$DEB_WORK"
  ar rcs "$OUT_DIR/deskflow_${VERSION}_amd64.deb" debian-binary control.tar.gz data.tar.gz
)

echo "[3/4] Building Fedora/RHEL RPM package (.rpm)..."
RPM_TOP="$BUILD_TMP/rpmbuild"
mkdir -p "$RPM_TOP"/{BUILD,RPMS,SOURCES,SPECS,SRPMS}
tar -C "$BUILD_TMP" -czf "$RPM_TOP/SOURCES/deskflow-${VERSION}.tar.gz" "DeskFlow-${VERSION}-linux-x86_64"

cat > "$RPM_TOP/SPECS/deskflow.spec" <<EOF
Name:           deskflow
Version:        ${VERSION}
Release:        1%{?dist}
Summary:        Deskflow Native Linux Control Center (GTK4 + Libadwaita)
License:        MIT
BuildArch:      x86_64
Requires:       python3, python3-gobject, gtk4, libadwaita

%description
Native GTK4 + Libadwaita desktop application for the DeskFlow ecosystem.

%install
mkdir -p %{buildroot}/usr/bin
mkdir -p %{buildroot}/usr/share/deskflow/src
mkdir -p %{buildroot}/usr/share/applications
mkdir -p %{buildroot}/usr/share/metainfo
mkdir -p %{buildroot}/usr/share/icons/hicolor/scalable/apps
cp -a "$LINUX_DIR/bin/deskflow" %{buildroot}/usr/bin/deskflow
chmod 0755 %{buildroot}/usr/bin/deskflow
cp -a "$LINUX_DIR/src/deskflow" %{buildroot}/usr/share/deskflow/src/
cp -a "$LINUX_DIR/data/com.deskflow.Station.desktop" %{buildroot}/usr/share/applications/
cp -a "$LINUX_DIR/data/com.deskflow.Station.metainfo.xml" %{buildroot}/usr/share/metainfo/
cp -a "$ROOT_DIR/shared/assets/deskflow-icon.svg" %{buildroot}/usr/share/icons/hicolor/scalable/apps/com.deskflow.Station.svg

%files
/usr/bin/deskflow
/usr/share/deskflow/src/deskflow
/usr/share/applications/com.deskflow.Station.desktop
/usr/share/metainfo/com.deskflow.Station.metainfo.xml
/usr/share/icons/hicolor/scalable/apps/com.deskflow.Station.svg
EOF

rpmbuild --define "_topdir $RPM_TOP" -bb "$RPM_TOP/SPECS/deskflow.spec" >/dev/null
find "$RPM_TOP/RPMS" -name "*.rpm" -exec cp -f {} "$OUT_DIR/deskflow-${VERSION}-1.x86_64.rpm" \;

echo "[4/4] Building self-extracting AppImage bundle..."
APPDIR="$BUILD_TMP/DeskFlow.AppDir"
mkdir -p "$APPDIR/usr/bin" "$APPDIR/usr/share/deskflow/src"
cp -a "$LINUX_DIR/bin/deskflow" "$APPDIR/usr/bin/deskflow"
cp -a "$LINUX_DIR/src/deskflow" "$APPDIR/usr/share/deskflow/src/"
cp -a "$LINUX_DIR/data/com.deskflow.Station.desktop" "$APPDIR/com.deskflow.Station.desktop"
cp -a "$ROOT_DIR/shared/assets/deskflow-icon.svg" "$APPDIR/com.deskflow.Station.svg"
cat > "$APPDIR/AppRun" <<'EOF'
#!/usr/bin/env bash
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export PYTHONPATH="$HERE/usr/share/deskflow/src:${PYTHONPATH:-}"
exec python3 "$HERE/usr/bin/deskflow" "$@"
EOF
chmod 0755 "$APPDIR/AppRun"

APPDIR_TAR="$BUILD_TMP/appdir.tar.gz"
tar -C "$APPDIR" -czf "$APPDIR_TAR" .
APPIMAGE_OUT="$OUT_DIR/DeskFlow-${VERSION}-x86_64.AppImage"
cat > "$APPIMAGE_OUT" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
TMP_APPDIR="$(mktemp -d)"
trap 'rm -rf "$TMP_APPDIR"' EXIT
ARCHIVE_LINE="$(awk '/^__APPDIR_ARCHIVE_BELOW__/ {print NR + 1; exit 0; }' "$0")"
tail -n +"$ARCHIVE_LINE" "$0" | tar -xzf - -C "$TMP_APPDIR"
exec "$TMP_APPDIR/AppRun" "$@"
__APPDIR_ARCHIVE_BELOW__
EOF
cat "$APPDIR_TAR" >> "$APPIMAGE_OUT"
chmod 0755 "$APPIMAGE_OUT"

echo "Linux packaging complete:"
ls -lh "$OUT_DIR"
