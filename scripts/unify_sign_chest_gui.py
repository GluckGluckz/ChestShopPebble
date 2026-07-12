#!/usr/bin/env python3
from pathlib import Path

path = Path("core/src/main/java/me/deadlight/ezchestshop/listeners/SignShopListener.java")
source = path.read_text(encoding="utf-8")

legacy_imports = """import me.deadlight.ezchestshop.guis.AdminShopGUI;
import me.deadlight.ezchestshop.guis.NonOwnerShopGUI;
import me.deadlight.ezchestshop.guis.OwnerShopGUI;
import me.deadlight.ezchestshop.guis.ServerShopGUI;
"""
new_import = "import me.deadlight.ezchestshop.guis.MultiItemShopGUI;\n"
if legacy_imports not in source:
    raise SystemExit("Legacy GUI import block was not found")
source = source.replace(legacy_imports, new_import, 1)

legacy_fields = """    private final NonOwnerShopGUI nonOwnerShopGUI = new NonOwnerShopGUI();
    private final OwnerShopGUI ownerShopGUI = new OwnerShopGUI();
    private final AdminShopGUI adminShopGUI = new AdminShopGUI();
"""
new_field = "    private final MultiItemShopGUI shopGUI = new MultiItemShopGUI();\n"
if legacy_fields not in source:
    raise SystemExit("Legacy GUI fields were not found")
source = source.replace(legacy_fields, new_field, 1)

legacy_admin_open = "            new ServerShopGUI().showGUI(player, data, shopBlock);"
if legacy_admin_open not in source:
    raise SystemExit("Legacy admin-shop GUI open was not found")
source = source.replace(legacy_admin_open, "            shopGUI.showGUI(player, shopBlock);", 1)

legacy_player_open = """        if (player.hasPermission("ecs.admin") || player.hasPermission("ecs.admin.view")) {
            adminShopGUI.showGUI(player, data, shopBlock);
        } else if (player.getUniqueId().toString().equalsIgnoreCase(ownerUuid) || isAdmin) {
            ownerShopGUI.showGUI(player, data, shopBlock, isAdmin);
        } else {
            nonOwnerShopGUI.showGUI(player, data, shopBlock);
        }
"""
if legacy_player_open not in source:
    raise SystemExit("Legacy player GUI routing block was not found")
source = source.replace(legacy_player_open, "        shopGUI.showGUI(player, shopBlock);\n", 1)

for legacy_name in ("AdminShopGUI", "NonOwnerShopGUI", "OwnerShopGUI", "ServerShopGUI"):
    if legacy_name in source:
        raise SystemExit(f"Legacy GUI reference remains: {legacy_name}")

path.write_text(source, encoding="utf-8")
print("SignShopListener now routes every sign click through MultiItemShopGUI")
