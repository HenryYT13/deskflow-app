#!/usr/bin/env python3
"""Entry point for the DeskFlow Native Linux GTK4 + Libadwaita Desktop Application."""

from __future__ import annotations

import sys
from typing import List, Optional

import gi

gi.require_version("Gtk", "4.0")
gi.require_version("Adw", "1")
from gi.repository import Adw, Gio, GLib, Gtk

from . import __app_id__, __version__
from .models import ConfigStore
from .window import DeskFlowMainWindow


class DeskFlowApplication(Adw.Application):
    """Libadwaita application wrapper for DeskFlow Linux."""

    def __init__(self, self_test: bool = False) -> None:
        super().__init__(
            application_id=__app_id__,
            flags=Gio.ApplicationFlags.NON_UNIQUE,
        )
        self.self_test = self_test
        self.main_window: Optional[DeskFlowMainWindow] = None

    def do_startup(self) -> None:
        Adw.Application.do_startup(self)

        display = Gtk.Widget.get_default_direction()
        from gi.repository import Gdk
        default_display = Gdk.Display.get_default()
        if default_display:
            import pathlib
            theme = Gtk.IconTheme.get_for_display(default_display)
            data_dir = pathlib.Path(__file__).resolve().parent.parent.parent / "data"
            if data_dir.is_dir():
                theme.add_search_path(str(data_dir))
        Gtk.Window.set_default_icon_name("com.deskflow.Station")

        quit_action = Gio.SimpleAction.new("quit", None)
        quit_action.connect("activate", lambda *_: self.quit())
        self.add_action(quit_action)
        self.set_accels_for_action("app.quit", ["<Primary>q"])

        refresh_action = Gio.SimpleAction.new("refresh", None)
        refresh_action.connect(
            "activate",
            lambda *_: self.main_window.refresh_all_async() if self.main_window else None,
        )
        self.add_action(refresh_action)
        self.set_accels_for_action("app.refresh", ["<Primary>r"])

    def do_activate(self) -> None:
        if not self.main_window:
            self.main_window = DeskFlowMainWindow(application=self)
        self.main_window.present()

        if self.self_test:
            GLib.timeout_add(350, self._run_self_test_sequence)

    def _run_self_test_sequence(self) -> bool:
        assert self.main_window is not None
        for page_id, _title, _icon in DeskFlowMainWindow.PAGES:
            self.main_window.content_stack.set_visible_child_name(page_id)
            assert self.main_window.content_stack.get_visible_child_name() == page_id
        print(
            f"[DeskFlow-GTK4] Self-test passed: Gtk {Gtk.get_major_version()}.{Gtk.get_minor_version()}, "
            f"Adw {Adw.get_major_version()}.{Adw.get_minor_version()}, "
            f"{len(DeskFlowMainWindow.PAGES)} pages verified."
        )
        self.quit()
        return False


def main(argv: Optional[List[str]] = None) -> int:
    args = list(sys.argv if argv is None else argv)
    if "--version" in args:
        print(f"Deskflow Linux (GTK4 + Libadwaita) v{__version__}")
        return 0

    self_test = "--self-test" in args
    clean_args = [a for a in args if a != "--self-test"]
    app = DeskFlowApplication(self_test=self_test)
    return app.run(clean_args)


if __name__ == "__main__":
    raise SystemExit(main())
