"""Merge chat/roles/profile extras and remaining page-help playbooks into en/es/fr."""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "src" / "lib" / "i18n" / "locales"


def deep_merge(base: dict, extra: dict) -> dict:
    out = dict(base)
    for key, value in extra.items():
        if isinstance(value, dict) and isinstance(out.get(key), dict):
            out[key] = deep_merge(out[key], value)
        else:
            out[key] = value
    return out


CHAT = {
    "en": {
        "title": "Operations Co-Pilot",
        "open": "Open support copilot",
        "close": "Close support copilot",
        "intro": "Ask how to use this screen. I can spotlight buttons, draft a safe action for you to approve, or walk a training scenario without touching live stock.",
        "unreachable": "Support chat is unavailable right now. Try again in a moment.",
        "trainingActive": "TRAINING SIMULATOR ACTIVE{{role}}",
        "askPlaceholder": "Ask a question…",
        "listening": "Listening…",
        "attachPhoto": "Attach photo",
        "pushToTalk": "Push to talk",
        "stopVoice": "Stop voice input",
        "send": "Send",
        "signedIn": "Signed in",
        "approveExecute": "Approve & Execute",
        "working": "Working…",
        "running": "Running…",
        "executed": "Executed",
        "draftFailed": "Could not execute — check the zone or try the on-screen button.",
        "photoFailed": "Could not prepare that photo. Try a smaller image.",
        "practice": "Practice: {{title}}",
        "trainingMode": "Training mode: **{{title}}**. Live stock will not change.",
    },
    "es": {
        "title": "Copiloto de operaciones",
        "open": "Abrir copiloto de soporte",
        "close": "Cerrar copiloto de soporte",
        "intro": "Pregunta cómo usar esta pantalla. Puedo resaltar botones, preparar una acción segura para que la apruebes o guiar un escenario de práctica sin tocar el stock real.",
        "unreachable": "El chat de soporte no está disponible ahora. Inténtalo en un momento.",
        "trainingActive": "SIMULADOR DE ENTRENAMIENTO ACTIVO{{role}}",
        "askPlaceholder": "Haz una pregunta…",
        "listening": "Escuchando…",
        "attachPhoto": "Adjuntar foto",
        "pushToTalk": "Mantén para hablar",
        "stopVoice": "Detener entrada de voz",
        "send": "Enviar",
        "signedIn": "Sesión iniciada",
        "approveExecute": "Aprobar y ejecutar",
        "working": "Trabajando…",
        "running": "Ejecutando…",
        "executed": "Ejecutado",
        "draftFailed": "No se pudo ejecutar — revisa la zona o usa el botón en pantalla.",
        "photoFailed": "No se pudo preparar esa foto. Prueba con una imagen más pequeña.",
        "practice": "Práctica: {{title}}",
        "trainingMode": "Modo de entrenamiento: **{{title}}**. El stock real no cambiará.",
    },
    "fr": {
        "title": "Copilote opérations",
        "open": "Ouvrir le copilote d’assistance",
        "close": "Fermer le copilote d’assistance",
        "intro": "Demandez comment utiliser cet écran. Je peux mettre des boutons en évidence, préparer une action sûre à approuver, ou guider un scénario d’entraînement sans toucher au stock réel.",
        "unreachable": "Le chat d’assistance est indisponible pour le moment. Réessayez dans un instant.",
        "trainingActive": "SIMULATEUR D’ENTRAÎNEMENT ACTIF{{role}}",
        "askPlaceholder": "Posez une question…",
        "listening": "Écoute…",
        "attachPhoto": "Joindre une photo",
        "pushToTalk": "Appuyer pour parler",
        "stopVoice": "Arrêter la saisie vocale",
        "send": "Envoyer",
        "signedIn": "Connecté",
        "approveExecute": "Approuver et exécuter",
        "working": "Traitement…",
        "running": "En cours…",
        "executed": "Exécuté",
        "draftFailed": "Échec de l’exécution — vérifiez la zone ou utilisez le bouton à l’écran.",
        "photoFailed": "Impossible de préparer cette photo. Essayez une image plus petite.",
        "practice": "Pratique : {{title}}",
        "trainingMode": "Mode entraînement : **{{title}}**. Le stock réel ne changera pas.",
    },
}

ROLES = {
    "en": {
        "OWNER": "Owners",
        "ADMIN": "Administrators",
        "WAREHOUSE_MANAGER": "Warehouse Managers",
        "PICKER": "Floor Pickers",
        "VIEWER": "Viewers",
        "B2B_CUSTOMER": "B2B Buyers",
    },
    "es": {
        "OWNER": "Propietarios",
        "ADMIN": "Administradores",
        "WAREHOUSE_MANAGER": "Gerentes de almacén",
        "PICKER": "Preparadores de piso",
        "VIEWER": "Consultores",
        "B2B_CUSTOMER": "Compradores B2B",
    },
    "fr": {
        "OWNER": "Propriétaires",
        "ADMIN": "Administrateurs",
        "WAREHOUSE_MANAGER": "Responsables d’entrepôt",
        "PICKER": "Préparateurs au sol",
        "VIEWER": "Lecteurs",
        "B2B_CUSTOMER": "Acheteurs B2B",
    },
}

PROFILE = {
    "en": {
        "photoHelp": "Update your profile photo for the office header",
        "uploadPhoto": "Upload profile photo",
        "photoCompressed": "Photos are compressed on-device before upload for a fast header avatar.",
        "savePersonal": "Save personal settings",
    },
    "es": {
        "photoHelp": "Actualiza tu foto de perfil para el encabezado de oficina",
        "uploadPhoto": "Subir foto de perfil",
        "photoCompressed": "Las fotos se comprimen en el dispositivo antes de subirlas para un avatar rápido.",
        "savePersonal": "Guardar ajustes personales",
    },
    "fr": {
        "photoHelp": "Mettez à jour votre photo de profil pour l’en-tête bureau",
        "uploadPhoto": "Téléverser une photo de profil",
        "photoCompressed": "Les photos sont compressées sur l’appareil avant l’envoi pour un avatar rapide.",
        "savePersonal": "Enregistrer les paramètres personnels",
    },
}


def pb(title, description, purpose, markdown, data_origin, flow, reversals, correlations, actions=None, troubleshooting=None):
    out = {
        "title": title,
        "description": description,
        "purpose": purpose,
        "markdown": markdown,
        "dataOrigin": data_origin,
        "flow": {str(i): s for i, s in enumerate(flow)},
        "reversals": {str(i): s for i, s in enumerate(reversals)},
        "correlations": {str(i): s for i, s in enumerate(correlations)},
    }
    if actions:
        out["actions"] = {str(i): s for i, s in enumerate(actions)}
    if troubleshooting:
        out["troubleshooting"] = {str(i): v for i, v in enumerate(troubleshooting)}
    return out


# Extra fields for the original 9 playbooks (title/description/markdown already exist).
EXISTING_EXTRAS = {
    "dashboard": {
        "en": pb(
            "Command Center",
            "Your daily overview of warehouse operations, active tasks, and system health.",
            "See pending work, inbound freight, and labor at a glance so you can jump into today's queue.",
            "The dashboard monitors pending shipments, incoming deliveries, and worker velocity. Use the quick actions below to jump directly into your daily queue.",
            "Live warehouse and office activity summarized for your roles.",
            ["Scan the KPIs for blocked orders, inbound, and labor.", "Open a quick action to jump into the matching queue."],
            ["The dashboard is read-only — reverse work on the operational page it links to."],
            ["Owners also see live paid-invoice signals from finance."],
        ),
        "es": pb(
            "Centro de mando",
            "Tu vista diaria de operaciones de almacén, tareas activas y salud del sistema.",
            "Ve el trabajo pendiente, la carga entrante y la mano de obra para saltar a la cola del día.",
            "El panel vigila envíos pendientes, entregas entrantes y la velocidad del equipo. Usa las acciones rápidas para saltar a tu cola del día.",
            "Actividad de almacén y oficina resumida según tus roles.",
            ["Revisa los KPI de pedidos bloqueados, entrada y mano de obra.", "Abre una acción rápida para ir a la cola correspondiente."],
            ["El panel es de solo lectura — deshaz el trabajo en la página operativa a la que enlaza."],
            ["Los propietarios también ven señales de facturas pagadas en vivo."],
        ),
        "fr": pb(
            "Centre de commande",
            "Votre vue quotidienne des opérations d’entrepôt, des tâches actives et de la santé du système.",
            "Voyez le travail en attente, le fret entrant et la main-d’œuvre pour rejoindre la file du jour.",
            "Le tableau de bord surveille les expéditions en attente, les livraisons entrantes et la vélocité des équipes. Utilisez les actions rapides pour rejoindre votre file du jour.",
            "Activité d’entrepôt et de bureau résumée selon vos rôles.",
            ["Parcourez les KPI des commandes bloquées, de l’entrée et de la main-d’œuvre.", "Ouvrez une action rapide pour rejoindre la file correspondante."],
            ["Le tableau de bord est en lecture seule — inversez le travail sur la page opérationnelle liée."],
            ["Les propriétaires voient aussi les signaux de factures payées en direct."],
        ),
    },
    "salesOrders": {
        "en": {"purpose": "Confirm customer demand and allocate stock so orders can enter the fulfillment queue.", "dataOrigin": "Sales orders entered by the office team or synced from connected storefronts.", "flow": {"0": "Confirm the order.", "1": "Allocate stock.", "2": "Release into fulfillment."}, "reversals": {"0": "Un-allocate releases reserved stock back to the pool."}, "correlations": {"0": "Allocated orders drop into the Fulfillment wave queue."}},
        "es": {"purpose": "Confirma la demanda del cliente y asigna stock para que el pedido entre a cumplimiento.", "dataOrigin": "Pedidos de venta capturados por oficina o sincronizados desde tiendas conectadas.", "flow": {"0": "Confirma el pedido.", "1": "Asigna stock.", "2": "Libera a cumplimiento."}, "reversals": {"0": "Desasignar libera el stock reservado de vuelta al pool."}, "correlations": {"0": "Los pedidos asignados entran a la cola de oleadas de Cumplimiento."}},
        "fr": {"purpose": "Confirmez la demande client et allouez le stock pour que la commande entre en exécution.", "dataOrigin": "Commandes saisies au bureau ou synchronisées depuis les boutiques connectées.", "flow": {"0": "Confirmez la commande.", "1": "Allouez le stock.", "2": "Libérez vers l’exécution."}, "reversals": {"0": "Désallouer libère le stock réservé vers le pool."}, "correlations": {"0": "Les commandes allouées rejoignent la file de vagues d’exécution."}},
    },
    "fulfillment": {
        "en": {"purpose": "Group orders into waves, pick from shelves, and pack for shipping.", "dataOrigin": "Allocated sales orders waiting to be picked and packed.", "flow": {"0": "Generate a wave.", "1": "Claim the wave to your scanner.", "2": "Pick, pack, and ship."}, "reversals": {"0": "Un-allocate or exception the line rather than inventing a stock adjust."}, "correlations": {"0": "Skip & Flag incidents land on the Exceptions desk."}},
        "es": {"purpose": "Agrupa pedidos en oleadas, recolecta de estantes y empaca para envío.", "dataOrigin": "Pedidos de venta asignados que esperan picking y empaque.", "flow": {"0": "Genera una oleada.", "1": "Reclama la oleada en tu escáner.", "2": "Recolecta, empaca y envía."}, "reversals": {"0": "Desasigna o marca excepción en lugar de inventar un ajuste de stock."}, "correlations": {"0": "Las incidencias Saltar y marcar llegan al escritorio de Excepciones."}},
        "fr": {"purpose": "Regroupez les commandes en vagues, prélevez en rayon et emballez pour l’expédition.", "dataOrigin": "Commandes allouées en attente de picking et d’emballage.", "flow": {"0": "Générez une vague.", "1": "Réclamez la vague sur votre scanner.", "2": "Prélevez, emballez et expédiez."}, "reversals": {"0": "Désallouez ou signalez une exception plutôt que d’inventer un ajustement."}, "correlations": {"0": "Les incidents Skip & Flag arrivent au bureau des Exceptions."}},
    },
    "purchaseOrders": {
        "en": {"purpose": "Order new stock from your suppliers and notify the receiving dock.", "dataOrigin": "Purchase orders drafted by purchasing.", "flow": {"0": "Draft a PO.", "1": "Confirm so receiving expects the delivery."}, "reversals": {"0": "Cancel an unreceived PO; received lines reverse through returns or stock correction."}, "correlations": {"0": "Confirmed POs appear on Inbound Receiving."}},
        "es": {"purpose": "Pide stock nuevo a tus proveedores y avisa al muelle de recepción.", "dataOrigin": "Órdenes de compra redactadas por compras.", "flow": {"0": "Redacta una OC.", "1": "Confirma para que recepción espere la entrega."}, "reversals": {"0": "Cancela una OC no recibida; las líneas recibidas se revierten por devoluciones o corrección."}, "correlations": {"0": "Las OC confirmadas aparecen en Recepción de entrada."}},
        "fr": {"purpose": "Commandez du stock auprès des fournisseurs et prévenez le quai de réception.", "dataOrigin": "Bons de commande rédigés par les achats.", "flow": {"0": "Rédigez un BC.", "1": "Confirmez pour que la réception attende la livraison."}, "reversals": {"0": "Annulez un BC non reçu ; les lignes reçues s’inversent par retours ou correction."}, "correlations": {"0": "Les BC confirmés apparaissent en Réception entrante."}},
    },
    "inboundReceive": {
        "en": {"purpose": "Check in newly arrived inventory from suppliers and generate LPNs for putaway.", "dataOrigin": "Inbound receipts against confirmed purchase orders.", "flow": {"0": "Scan the incoming PO.", "1": "Verify quantities.", "2": "Generate LPNs and start put-away."}, "reversals": {"0": "Use the undo window before commit; after commit reverse via RTV or stock correction."}, "correlations": {"0": "Over-receipts may require a return-to-vendor."}},
        "es": {"purpose": "Da de alta inventario recién llegado y genera LPN para ubicación.", "dataOrigin": "Recibos de entrada contra órdenes de compra confirmadas.", "flow": {"0": "Escanea la OC entrante.", "1": "Verifica cantidades.", "2": "Genera LPN e inicia ubicación."}, "reversals": {"0": "Usa la ventana de deshacer antes de confirmar; después revierte por RTV o corrección."}, "correlations": {"0": "Los sobre-recibos pueden requerir devolución a proveedor."}},
        "fr": {"purpose": "Enregistrez l’inventaire arrivé et générez des LPN pour le rangement.", "dataOrigin": "Réceptions entrantes contre des bons de commande confirmés.", "flow": {"0": "Scannez le BC entrant.", "1": "Vérifiez les quantités.", "2": "Générez des LPN et démarrez le rangement."}, "reversals": {"0": "Utilisez la fenêtre d’annulation avant validation ; ensuite inversez par RTV ou correction."}, "correlations": {"0": "Les sur-réceptions peuvent exiger un retour fournisseur."}},
    },
    "products": {
        "en": {"purpose": "The master catalog of everything you sell and store.", "dataOrigin": "Product catalog with current on-hand quantities.", "flow": {"0": "Add or edit a SKU.", "1": "Set barcodes and units.", "2": "Import from CSV when loading many rows."}, "reversals": {"0": "Edit the master again; catalog edits do not erase stock history."}, "correlations": {"0": "Changes sync across facilities and feed barcode resolution on the floor."}},
        "es": {"purpose": "El catálogo maestro de todo lo que vendes y almacenas.", "dataOrigin": "Catálogo de productos con existencias actuales.", "flow": {"0": "Añade o edita un SKU.", "1": "Define códigos de barras y unidades.", "2": "Importa CSV cuando cargues muchas filas."}, "reversals": {"0": "Vuelve a editar el maestro; los cambios de catálogo no borran el historial de stock."}, "correlations": {"0": "Los cambios se sincronizan entre instalaciones y alimentan códigos de barras en el piso."}},
        "fr": {"purpose": "Le catalogue maître de tout ce que vous vendez et stockez.", "dataOrigin": "Catalogue produits avec les quantités en stock actuelles.", "flow": {"0": "Ajoutez ou modifiez un SKU.", "1": "Définissez codes-barres et unités.", "2": "Importez un CSV pour de nombreuses lignes."}, "reversals": {"0": "Modifiez à nouveau le master ; les éditions catalogue n’effacent pas l’historique stock."}, "correlations": {"0": "Les changements se synchronisent entre sites et alimentent la résolution codes-barres au sol."}},
    },
    "cycleCounts": {
        "en": {"purpose": "Blind physical counts that reconcile on-hand to reality.", "dataOrigin": "Cycle count worksheets and variance approvals.", "flow": {"0": "Open a count task (expected qty hidden).", "1": "Enter the physical quantity.", "2": "Managers approve large variances."}, "reversals": {"0": "Before confirm, clear the entry and re-count.", "1": "After an auto-adjust, reverse with a manager stock correction."}, "correlations": {"0": "Approved counts write a stock correction and refresh on-hand quantities."}},
        "es": {"purpose": "Conteos físicos ciegos que concilian existencias con la realidad.", "dataOrigin": "Hojas de conteo cíclico y aprobaciones de varianza.", "flow": {"0": "Abre una tarea de conteo (cantidad esperada oculta).", "1": "Ingresa la cantidad física.", "2": "Los gerentes aprueban varianzas grandes."}, "reversals": {"0": "Antes de confirmar, borra y vuelve a contar.", "1": "Tras un autoajuste, revierte con una corrección del gerente."}, "correlations": {"0": "Los conteos aprobados escriben una corrección y refrescan existencias."}},
        "fr": {"purpose": "Comptages physiques à l’aveugle qui rapprochent le stock de la réalité.", "dataOrigin": "Feuilles d’inventaire tournant et approbations d’écart.", "flow": {"0": "Ouvrez une tâche de comptage (qté attendue masquée).", "1": "Saisissez la quantité physique.", "2": "Les managers approuvent les grands écarts."}, "reversals": {"0": "Avant confirmation, effacez et recomptez.", "1": "Après un auto-ajustement, inversez par une correction manager."}, "correlations": {"0": "Les comptages approuvés écrivent une correction et rafraîchissent le stock."}},
    },
    "exceptions": {
        "en": {"purpose": "Resolve floor Skip & Flag incidents and parked offline scan conflicts.", "dataOrigin": "Lines pickers marked as Skip & Flag and parked offline scans.", "flow": {"0": "Open an OPEN fulfillment exception.", "1": "Investigate, then Resolve with disposition.", "2": "On Sync Conflicts, Approve & Re-process or Discard."}, "reversals": {"0": "Resolving an exception does not automatically restore the original allocation."}, "correlations": {"0": "Pickers create exceptions to keep waves moving; managers close the loop."}},
        "es": {"purpose": "Resuelve incidencias Saltar y marcar y conflictos de escaneo offline.", "dataOrigin": "Líneas marcadas por pickers y escaneos offline aparcados.", "flow": {"0": "Abre una excepción ABIERTA de cumplimiento.", "1": "Investiga y Resuelve con disposición.", "2": "En Conflictos de sync, Aprueba y reprocesa o Descarta."}, "reversals": {"0": "Resolver una excepción no restaura automáticamente la asignación original."}, "correlations": {"0": "Los pickers crean excepciones para seguir; los gerentes cierran el ciclo."}},
        "fr": {"purpose": "Résolvez les incidents Skip & Flag et les conflits de scan hors ligne.", "dataOrigin": "Lignes signalées par les préparateurs et scans hors ligne en attente.", "flow": {"0": "Ouvrez une exception OUVERTE d’exécution.", "1": "Enquêtez, puis Résolvez avec disposition.", "2": "Sur Conflits de sync, Approuvez et retraitez ou Jetez."}, "reversals": {"0": "Résoudre une exception ne restaure pas automatiquement l’allocation d’origine."}, "correlations": {"0": "Les préparateurs créent des exceptions pour avancer ; les managers bouclent."}},
    },
    "settings": {
        "en": {"purpose": "Tenant-wide configuration hub for users, warehouses, floor rules, and integrations.", "dataOrigin": "Company settings your administrators maintain.", "flow": {"0": "Open the tab that matches the change you need.", "1": "Save — the audit log records the actor.", "2": "Confirm floor devices pick up the new rule."}, "reversals": {"0": "Toggle the rule back; every change is recorded in the audit history."}, "correlations": {"0": "Blind receiving and variance thresholds change what pickers may post without manager review."}},
        "es": {"purpose": "Hub de configuración del inquilino: usuarios, almacenes, reglas de piso e integraciones.", "dataOrigin": "Ajustes de empresa que mantienen los administradores.", "flow": {"0": "Abre la pestaña del cambio que necesitas.", "1": "Guarda: el registro de auditoría anota al actor.", "2": "Confirma que los dispositivos tomen la nueva regla."}, "reversals": {"0": "Vuelve a cambiar la regla; cada cambio queda en auditoría."}, "correlations": {"0": "Recepción ciega y umbrales de varianza cambian lo que los pickers pueden publicar sin revisión."}},
        "fr": {"purpose": "Hub de configuration du locataire : utilisateurs, entrepôts, règles sol et intégrations.", "dataOrigin": "Paramètres d’entreprise tenus par les administrateurs.", "flow": {"0": "Ouvrez l’onglet correspondant au changement.", "1": "Enregistrez — le journal d’audit note l’acteur.", "2": "Confirmez que les appareils prennent la nouvelle règle."}, "reversals": {"0": "Remettez la règle ; chaque changement est audité."}, "correlations": {"0": "Réception à l’aveugle et seuils d’écart changent ce que les préparateurs peuvent poster sans revue."}},
    },
}

# Remaining routes — full playbooks in three languages.
REMAINING = {
    "inventory_ledger": {
        "en": pb(
            "Inventory Ledger",
            "Review stock movements. History is never erased — corrections add a new row.",
            "Review stock movements (receive, adjust, ship, transfer, assemble). History is never erased — corrections add a new row.",
            "Filter movements, open a row for reason and actor, and reverse only with the built-in Undo when policy allows.",
            "Stock movement history shown for managers (every receive, pick, and adjust).",
            ["Filter by SKU, location, date, or movement type.", "Open a row to see reason code, actor, lot/serial, and references.", "Use Undo only on reversible rows when policy allows."],
            ["Prefer the built-in Undo — it posts a compensating stock correction attributed to you.", "Never delete historical stock movements.", "If Undo is hidden, post an explicit stock correction with a clear reason."],
            ["Every floor scan and office allocate/ship eventually appears here.", "Lot Trace walks these rows for recall response."],
        ),
        "es": pb(
            "Libro de inventario",
            "Revisa movimientos de stock. El historial no se borra: las correcciones añaden una fila nueva.",
            "Revisa movimientos (recibo, ajuste, envío, traslado, ensamble). El historial nunca se borra.",
            "Filtra movimientos, abre una fila para ver motivo y actor, y revierte solo con Deshacer cuando la política lo permita.",
            "Historial de movimientos de stock para gerentes (cada recibo, picking y ajuste).",
            ["Filtra por SKU, ubicación, fecha o tipo de movimiento.", "Abre una fila para ver motivo, actor, lote/serie y referencias.", "Usa Deshacer solo en filas reversibles cuando la política lo permita."],
            ["Prefiere Deshacer integrado: publica una corrección compensatoria a tu nombre.", "Nunca borres movimientos históricos.", "Si Deshacer está oculto, publica una corrección de stock con un motivo claro."],
            ["Cada escaneo de piso y asignación/envío de oficina aparece aquí.", "Trazabilidad de lote recorre estas filas en un recall."],
        ),
        "fr": pb(
            "Grand livre des stocks",
            "Consultez les mouvements de stock. L’historique n’est jamais effacé — les corrections ajoutent une ligne.",
            "Consultez les mouvements (réception, ajustement, expédition, transfert, assemblage). L’historique n’est jamais effacé.",
            "Filtrez les mouvements, ouvrez une ligne pour le motif et l’acteur, et n’annulez qu’avec Annuler lorsque la politique le permet.",
            "Historique des mouvements de stock pour les managers (chaque réception, prélèvement et ajustement).",
            ["Filtrez par SKU, emplacement, date ou type de mouvement.", "Ouvrez une ligne pour voir le motif, l’acteur, le lot/série et les références.", "Utilisez Annuler uniquement sur les lignes réversibles si la politique le permet."],
            ["Préférez Annuler intégré — cela poste une correction compensatoire à votre nom.", "N’effacez jamais les mouvements historiques.", "Si Annuler est masqué, postez une correction de stock avec un motif clair."],
            ["Chaque scan au sol et allocation/expédition bureau apparaît ici.", "La traçabilité de lot parcourt ces lignes pour un rappel."],
        ),
    },
    "replenishments": {
        "en": pb(
            "Replenishments",
            "Move stock from reserve into pick faces so outbound waves do not starve.",
            "Move stock from reserve/bulk into pick faces so outbound waves do not starve.",
            "Review suggested moves, scan source then destination, and confirm the transfer.",
            "Suggested moves from reserve storage into pick faces.",
            ["Review suggested movements (min/max rules + predictive triggers).", "Scan source, scan destination, confirm the transfer."],
            ["Before confirm, cancel the task.", "After confirm, reverse with an opposite transfer documented as a replenishment correction."],
            ["Prevents pick-line stockouts that would otherwise become Skip & Flag exceptions."],
        ),
        "es": pb(
            "Reposiciones",
            "Mueve stock de reserva a caras de picking para que las oleadas no se queden sin producto.",
            "Mueve stock de reserva/granel a caras de picking para que las oleadas de salida no se queden cortas.",
            "Revisa movimientos sugeridos, escanea origen y destino, y confirma el traslado.",
            "Movimientos sugeridos desde reserva hacia caras de picking.",
            ["Revisa movimientos sugeridos (mín/máx y disparadores predictivos).", "Escanea origen, destino y confirma el traslado."],
            ["Antes de confirmar, cancela la tarea.", "Después de confirmar, revierte con un traslado inverso documentado como corrección de reposición."],
            ["Evita quiebres en la línea de picking que se volverían excepciones Saltar y marcar."],
        ),
        "fr": pb(
            "Réapprovisionnements",
            "Déplacez le stock de réserve vers les faces de picking pour que les vagues ne manquent pas.",
            "Déplacez le stock de réserve/vrac vers les faces de picking pour que les vagues sortantes ne soient pas à sec.",
            "Examinez les mouvements suggérés, scannez source puis destination, et confirmez le transfert.",
            "Mouvements suggérés de la réserve vers les faces de picking.",
            ["Examinez les mouvements suggérés (règles min/max et déclencheurs prédictifs).", "Scannez la source, la destination, confirmez le transfert."],
            ["Avant confirmation, annulez la tâche.", "Après confirmation, inversez avec un transfert opposé documenté comme correction de réappro."],
            ["Évite les ruptures en ligne de picking qui deviendraient des exceptions Skip & Flag."],
        ),
    },
    "customers": {
        "en": pb(
            "Customers",
            "Maintain customer master data and credit lines that gate outbound allocation.",
            "Maintain customer master data and credit lines that gate outbound allocation.",
            "Create or edit a customer, set the credit limit, and link ship-to addresses used on sales orders.",
            "Customer records maintained by your office team.",
            ["Create or edit a customer profile.", "Set or adjust the line-of-credit limit.", "Link ship-to addresses used on sales orders."],
            ["Credit limit changes take effect on the next allocate.", "Deactivating a customer does not cancel open sales orders."],
            ["Over-limit allocate attempts freeze orders for review.", "B2B showroom price lists bind to these customer records."],
        ),
        "es": pb(
            "Clientes",
            "Mantén datos maestros de clientes y líneas de crédito que controlan la asignación de salida.",
            "Mantén datos maestros de clientes y líneas de crédito que controlan la asignación de salida.",
            "Crea o edita un cliente, define el límite de crédito y vincula direcciones de envío de los pedidos.",
            "Registros de clientes que mantiene tu equipo de oficina.",
            ["Crea o edita un perfil de cliente.", "Define o ajusta el límite de crédito.", "Vincula direcciones de envío usadas en pedidos de venta."],
            ["Los cambios de crédito aplican en la siguiente asignación.", "Desactivar un cliente no cancela pedidos abiertos."],
            ["Intentos sobre el límite congelan pedidos para revisión.", "Las listas de precios del showroom B2B se ligan a estos clientes."],
        ),
        "fr": pb(
            "Clients",
            "Maintenez les fiches clients et les lignes de crédit qui contrôlent l’allocation sortante.",
            "Maintenez les fiches clients et les lignes de crédit qui contrôlent l’allocation sortante.",
            "Créez ou modifiez un client, définissez la limite de crédit et liez les adresses de livraison des commandes.",
            "Fiches clients tenues par votre équipe bureau.",
            ["Créez ou modifiez un profil client.", "Définissez ou ajustez la limite de crédit.", "Liez les adresses de livraison utilisées sur les commandes."],
            ["Les changements de crédit s’appliquent à la prochaine allocation.", "Désactiver un client n’annule pas les commandes ouvertes."],
            ["Les allocations hors limite gèlent les commandes pour revue.", "Les tarifs du showroom B2B sont liés à ces fiches."],
        ),
    },
    "invoices": {
        "en": pb(
            "Invoices",
            "Track invoices and payment state; when a payment clears, the invoice shows PAID.",
            "Track invoices and payment state; when a payment clears, the invoice shows PAID for finance.",
            "Review AR aging, send payment requests when configured, and watch live PAID updates after settlement.",
            "Invoices created after orders ship or are ready to bill.",
            ["Review AR aging and open invoices.", "Send payment requests when configured.", "Watch live PAID updates after payment settles."],
            ["Do not manually flip PAID without a finance process.", "Refunds are accounting events; stock returns use the Returns module."],
            ["Paid invoices may release credit exposure for new allocations."],
        ),
        "es": pb(
            "Facturas",
            "Sigue facturas y el estado de pago; al liquidarse, la factura muestra PAGADA.",
            "Sigue facturas y el estado de pago; al liquidarse, la factura muestra PAGADA para finanzas.",
            "Revisa antigüedad de CXC, envía solicitudes de pago y observa actualizaciones PAGADA en vivo.",
            "Facturas creadas cuando los pedidos se envían o están listos para facturar.",
            ["Revisa antigüedad y facturas abiertas.", "Envía solicitudes de pago si está configurado.", "Observa actualizaciones PAGADA en vivo."],
            ["No marques PAGADA a mano sin un proceso de finanzas.", "Los reembolsos son eventos contables; las devoluciones de stock usan Devoluciones."],
            ["Las facturas pagadas pueden liberar crédito para nuevas asignaciones."],
        ),
        "fr": pb(
            "Factures",
            "Suivez les factures et l’état de paiement ; une fois réglée, la facture affiche PAYÉE.",
            "Suivez les factures et l’état de paiement ; une fois réglée, la facture affiche PAYÉE pour la finance.",
            "Examinez le vieillissement client, envoyez des demandes de paiement et suivez les mises à jour PAYÉE en direct.",
            "Factures créées après expédition ou lorsque la commande est prête à facturer.",
            ["Examinez le vieillissement et les factures ouvertes.", "Envoyez des demandes de paiement si configuré.", "Suivez les mises à jour PAYÉE en direct."],
            ["Ne passez pas manuellement en PAYÉE sans processus finance.", "Les remboursements sont comptables ; les retours stock utilisent Retours."],
            ["Les factures payées peuvent libérer du crédit pour de nouvelles allocations."],
        ),
    },
    "suppliers": {
        "en": pb(
            "Suppliers",
            "Vendor master — terms, lead times, quality ratings, and masked banking details.",
            "Vendor master — terms, lead times, quality ratings, and envelope-encrypted banking details.",
            "Create or edit a supplier, set lead times for replenishment planning, and store banking details (masked after save).",
            "Supplier records maintained by purchasing.",
            ["Create or edit a supplier profile.", "Set lead times that feed replenishment planning.", "Store banking details (shown masked after save)."],
            ["Correct master data with another edit.", "Deactivate rather than delete suppliers tied to historical POs."],
            ["PO creation requires an approved supplier.", "Lead times influence automated replenishment suggestions."],
        ),
        "es": pb(
            "Proveedores",
            "Maestro de proveedores: plazos, tiempos de entrega, calidad y datos bancarios enmascarados.",
            "Maestro de proveedores: plazos, tiempos de entrega, calidad y datos bancarios cifrados.",
            "Crea o edita un proveedor, define plazos para planificación y guarda datos bancarios (enmascarados al guardar).",
            "Registros de proveedores que mantiene compras.",
            ["Crea o edita un perfil de proveedor.", "Define plazos que alimentan la planificación de reposición.", "Guarda datos bancarios (enmascarados al guardar)."],
            ["Corrige datos maestros con otra edición.", "Desactiva en lugar de borrar proveedores ligados a OC históricas."],
            ["Crear una OC requiere un proveedor aprobado.", "Los plazos influyen en las sugerencias de reposición."],
        ),
        "fr": pb(
            "Fournisseurs",
            "Fiche fournisseur — conditions, délais, qualité et coordonnées bancaires masquées.",
            "Fiche fournisseur — conditions, délais, qualité et coordonnées bancaires chiffrées.",
            "Créez ou modifiez un fournisseur, définissez les délais pour le réappro, et stockez les coordonnées bancaires (masquées après enregistrement).",
            "Fiches fournisseurs tenues par les achats.",
            ["Créez ou modifiez un profil fournisseur.", "Définissez les délais qui alimentent le réappro.", "Enregistrez les coordonnées bancaires (masquées après save)."],
            ["Corrigez les données par une nouvelle édition.", "Désactivez plutôt que supprimer les fournisseurs liés à des BC historiques."],
            ["Créer un BC exige un fournisseur approuvé.", "Les délais influencent les suggestions de réappro."],
        ),
    },
    "returns": {
        "en": pb(
            "Returns / RMA",
            "Authorize customer returns, then receive and disposition (restock vs scrap) on the floor.",
            "Authorize customer returns, then receive and disposition (restock vs scrap) on the floor.",
            "Create and approve an RMA, receive against it on the floor, then restock or scrap.",
            "Customer return (RMA) documents.",
            ["Create and approve an RMA in the office.", "Floor receives against the RMA on Receive Returns.", "Disposition RESTOCK or SCRAP."],
            ["Cancel an unreceived RMA before floor intake.", "Restocked units that should not sell: move back to quarantine or scrap with a stock correction."],
            ["Restock increases sellable ATP; scrap does not.", "Finance may issue credits separately from inventory disposition."],
        ),
        "es": pb(
            "Devoluciones / RMA",
            "Autoriza devoluciones de clientes, luego recibe y dispone (reponer o desechar) en el piso.",
            "Autoriza devoluciones de clientes, luego recibe y dispone (reponer o desechar) en el piso.",
            "Crea y aprueba un RMA, recíbelo en el piso y luego repón o desecha.",
            "Documentos de devolución de clientes (RMA).",
            ["Crea y aprueba un RMA en oficina.", "El piso recibe contra el RMA en Recibir devoluciones.", "Disposición REPOSICIÓN o DESECHO."],
            ["Cancela un RMA no recibido antes del ingreso.", "Unidades repuestas que no deben venderse: vuelve a cuarentena o desecho con una corrección."],
            ["Reponer aumenta el ATP vendible; desechar no.", "Finanzas puede emitir créditos aparte de la disposición de inventario."],
        ),
        "fr": pb(
            "Retours / RMA",
            "Autorisez les retours clients, puis recevez et disposez (restock vs rebut) au sol.",
            "Autorisez les retours clients, puis recevez et disposez (restock vs rebut) au sol.",
            "Créez et approuvez un RMA, recevez-le au sol, puis restockez ou mettez au rebut.",
            "Documents de retour client (RMA).",
            ["Créez et approuvez un RMA au bureau.", "Le sol reçoit contre le RMA dans Recevoir les retours.", "Disposition RESTOCK ou REBUT."],
            ["Annulez un RMA non reçu avant l’entrée.", "Unités restockées qui ne doivent pas se vendre : quarantaine ou rebut avec une correction."],
            ["Le restock augmente l’ATP vendable ; le rebut non.", "La finance peut émettre des avoirs séparément de la disposition stock."],
        ),
    },
    "returns_receive": {
        "en": pb(
            "Returns Receive (Floor)",
            "Scan returned goods against an approved RMA and apply quarantine-aware disposition.",
            "Scan returned goods against an approved RMA and apply quarantine-aware disposition.",
            "Open the RMA on the handheld, scan the item, and complete putaway to quarantine or scrap as directed.",
            "Return receipts scanned on the floor.",
            ["Open the RMA on the handheld.", "Scan the returned item and confirm disposition path.", "Complete putaway to quarantine or scrap location as directed."],
            ["Mis-scan undo window applies before commit.", "After restock, only managers release or scrap with attributed ledger moves."],
            ["Closes RMA lines visible to office Returns.", "Quarantine release is an office decision."],
        ),
        "es": pb(
            "Recepción de devoluciones (piso)",
            "Escanea mercancía devuelta contra un RMA aprobado y aplica disposición con cuarentena.",
            "Escanea mercancía devuelta contra un RMA aprobado y aplica disposición con cuarentena.",
            "Abre el RMA en el handheld, escanea el artículo y completa la ubicación a cuarentena o desecho.",
            "Recibos de devolución escaneados en el piso.",
            ["Abre el RMA en el handheld.", "Escanea el artículo y confirma la disposición.", "Completa la ubicación a cuarentena o desecho según indiquen."],
            ["La ventana de deshacer aplica antes de confirmar.", "Tras reponer, solo gerentes liberan o desechan con movimientos atribuidos."],
            ["Cierra líneas RMA visibles en Devoluciones de oficina.", "Liberar cuarentena es decisión de oficina."],
        ),
        "fr": pb(
            "Réception des retours (sol)",
            "Scannez les retours contre un RMA approuvé et appliquez une disposition avec quarantaine.",
            "Scannez les retours contre un RMA approuvé et appliquez une disposition avec quarantaine.",
            "Ouvrez le RMA sur le terminal, scannez l’article et rangez en quarantaine ou rebut.",
            "Réceptions de retours scannées au sol.",
            ["Ouvrez le RMA sur le terminal.", "Scannez l’article retourné et confirmez la disposition.", "Rangez en quarantaine ou rebut selon les instructions."],
            ["La fenêtre d’annulation s’applique avant validation.", "Après restock, seuls les managers libèrent ou mettent au rebut."],
            ["Clôt les lignes RMA visibles au bureau Retours.", "La levée de quarantaine est une décision bureau."],
        ),
    },
    "compliance_lot_trace": {
        "en": pb(
            "Lot Trace",
            "Read-only genealogy of a lot or serial for recall and quality response.",
            "Read-only genealogy of a lot/serial across supplier, receive, assembly, ship, and customer.",
            "Enter the lot or serial, review the chain, and export CSV for regulators when needed.",
            "Lot and serial history for recalls and quality checks.",
            ["Enter the lot or serial number.", "Review the recursive ledger chain.", "Export CSV for regulators or customers when needed."],
            ["Lot Trace is read-only — nothing to undo here.", "Correct source data via returns, adjusts, or re-ship elsewhere."],
            ["VIEWER role is enough; operational roles write the underlying history."],
        ),
        "es": pb(
            "Trazabilidad de lote",
            "Genealogía de solo lectura de un lote o serie para recalls y calidad.",
            "Genealogía de solo lectura de un lote/serie desde proveedor, recibo, ensamble, envío y cliente.",
            "Ingresa el lote o serie, revisa la cadena y exporta CSV para reguladores si hace falta.",
            "Historial de lote y serie para recalls y calidad.",
            ["Ingresa el lote o número de serie.", "Revisa la cadena del libro.", "Exporta CSV para reguladores o clientes si hace falta."],
            ["Trazabilidad es de solo lectura — no hay nada que deshacer aquí.", "Corrige los datos de origen con devoluciones, ajustes o reenvíos."],
            ["El rol VIEWER basta; los roles operativos escriben el historial."],
        ),
        "fr": pb(
            "Traçabilité de lot",
            "Généalogie en lecture seule d’un lot ou d’un série pour les rappels et la qualité.",
            "Généalogie en lecture seule d’un lot/série du fournisseur à la réception, l’assemblage, l’expédition et le client.",
            "Saisissez le lot ou le série, examinez la chaîne, et exportez un CSV pour les régulateurs si besoin.",
            "Historique lot et série pour rappels et contrôles qualité.",
            ["Saisissez le lot ou le numéro de série.", "Examinez la chaîne du grand livre.", "Exportez un CSV pour régulateurs ou clients si besoin."],
            ["La traçabilité est en lecture seule — rien à annuler ici.", "Corrigez les données source via retours, ajustements ou réexpédition."],
            ["Le rôle VIEWER suffit ; les rôles opérationnels écrivent l’historique."],
        ),
    },
    "rtls": {
        "en": pb(
            "RTLS map",
            "Spatial digital twin — live picker positions, congestion heat, and walkable edges.",
            "Spatial digital twin — live picker positions, congestion heat, and walkable edges for wayfinding.",
            "Open the map, watch live positions, inspect the heatmap, and adjust coordinates when the layout changes.",
            "Live device or asset positions on the warehouse map.",
            ["Open the map and watch live position updates.", "Inspect the 7-day heatmap of stock movement activity.", "Adjust coordinates / edges when the physical layout changes."],
            ["Coordinate edits can be patched again; they do not reverse inventory.", "Heatmap history is analytical — not a transaction log to undo."],
            ["Pick pathing consumes this graph.", "Floor scans feed both ledger heat and live tags."],
        ),
        "es": pb(
            "Mapa RTLS",
            "Gemelo espacial: posiciones de picking en vivo, calor de congestión y bordes transitables.",
            "Gemelo espacial: posiciones de picking en vivo, calor de congestión y bordes transitables.",
            "Abre el mapa, observa posiciones en vivo, revisa el mapa de calor y ajusta coordenadas si cambia el layout.",
            "Posiciones en vivo de dispositivos o activos en el mapa del almacén.",
            ["Abre el mapa y observa actualizaciones de posición.", "Revisa el mapa de calor de 7 días.", "Ajusta coordenadas o bordes cuando cambie el layout físico."],
            ["Las coordenadas se pueden volver a editar; no revierten inventario.", "El mapa de calor es analítico, no un libro para deshacer."],
            ["El ruteo de picking usa este grafo.", "Los escaneos de piso alimentan el calor y las etiquetas en vivo."],
        ),
        "fr": pb(
            "Carte RTLS",
            "Jumeau spatial — positions picking en direct, chaleur de congestion et arêtes praticables.",
            "Jumeau spatial — positions picking en direct, chaleur de congestion et arêtes praticables.",
            "Ouvrez la carte, suivez les positions, inspectez la heatmap et ajustez les coordonnées si le layout change.",
            "Positions en direct des appareils ou actifs sur la carte d’entrepôt.",
            ["Ouvrez la carte et suivez les mises à jour de position.", "Inspectez la heatmap sur 7 jours.", "Ajustez coordonnées / arêtes quand le layout physique change."],
            ["Les coordonnées peuvent être re-patchées ; elles n’inversent pas le stock.", "La heatmap est analytique — pas un journal à annuler."],
            ["Le chemin de picking consomme ce graphe.", "Les scans au sol alimentent la chaleur et les tags live."],
        ),
    },
    "manufacturing_boms": {
        "en": pb(
            "Bills of Materials",
            "Define assembly recipes that drive production orders.",
            "Define assembly recipes (components, operations, co-products) that drive production orders.",
            "Create a BOM for a finished SKU, add component lines and operations, then save.",
            "Bills of materials that define what goes into a finished good.",
            ["Create a BOM for a finished SKU.", "Add component lines, operations, and outputs.", "Save — production orders will consume this recipe."],
            ["Edit or version the BOM before orders allocate against it.", "After production has run, supersede with a new revision instead of deleting history."],
            ["Component availability is checked when production orders allocate."],
        ),
        "es": pb(
            "Listas de materiales",
            "Define recetas de ensamble que impulsan las órdenes de producción.",
            "Define recetas de ensamble (componentes, operaciones, coproductos) que impulsan las órdenes de producción.",
            "Crea un BOM para un SKU terminado, añade componentes y operaciones, y guarda.",
            "Listas de materiales que definen qué entra en un producto terminado.",
            ["Crea un BOM para un SKU terminado.", "Añade componentes, operaciones y salidas.", "Guarda: las órdenes de producción usarán esta receta."],
            ["Edita o versiona el BOM antes de que las órdenes asignen contra él.", "Tras producir, sustituye con una revisión nueva; no borres el historial."],
            ["La disponibilidad de componentes se verifica al asignar órdenes de producción."],
        ),
        "fr": pb(
            "Nomenclatures",
            "Définissez les recettes d’assemblage qui pilotent les ordres de production.",
            "Définissez les recettes d’assemblage (composants, opérations, co-produits) qui pilotent les ordres de production.",
            "Créez une nomenclature pour un SKU fini, ajoutez composants et opérations, puis enregistrez.",
            "Nomenclatures qui définissent ce qui entre dans un produit fini.",
            ["Créez une nomenclature pour un SKU fini.", "Ajoutez composants, opérations et sorties.", "Enregistrez — les ordres de production consommeront cette recette."],
            ["Modifiez ou versionnez la nomenclature avant allocation.", "Après production, remplacez par une nouvelle révision au lieu d’effacer l’historique."],
            ["La disponibilité des composants est vérifiée à l’allocation des ordres."],
        ),
    },
    "manufacturing_orders": {
        "en": pb(
            "Production Orders",
            "Schedule work orders and allocate raw components so sales picks cannot steal reserved materials.",
            "Schedule work orders and allocate raw components so sales picks cannot steal reserved materials.",
            "Create a production order from a BOM, allocate components, then release to the Production Terminal.",
            "Manufacturing work orders.",
            ["Create a production order from a BOM.", "Allocate components.", "Release to the Production Terminal for assembly."],
            ["Deallocate components before assembly to return raw materials to ATP.", "After assemble posts, reverse with compensating entries — never erase completed production history."],
            ["Locks components away from outbound sales picks.", "Terminal labor timesheets attach cost to the order."],
        ),
        "es": pb(
            "Órdenes de producción",
            "Programa órdenes de trabajo y asigna componentes para que el picking de ventas no tome material reservado.",
            "Programa órdenes de trabajo y asigna componentes para que el picking de ventas no tome material reservado.",
            "Crea una orden desde un BOM, asigna componentes y libérala al Terminal de producción.",
            "Órdenes de trabajo de manufactura.",
            ["Crea una orden de producción desde un BOM.", "Asigna componentes.", "Libera al Terminal de producción para ensamble."],
            ["Desasigna componentes antes del ensamble para devolver materia prima al ATP.", "Tras ensamblar, revierte con asientos compensatorios — no borres el historial."],
            ["Bloquea componentes lejos del picking de ventas.", "Las hojas de tiempo del terminal imputan costo a la orden."],
        ),
        "fr": pb(
            "Ordres de production",
            "Planifiez les ordres et allouez les composants pour que le picking ventes ne prenne pas le matériau réservé.",
            "Planifiez les ordres et allouez les composants pour que le picking ventes ne prenne pas le matériau réservé.",
            "Créez un ordre depuis une nomenclature, allouez les composants, puis libérez vers le Terminal de production.",
            "Ordres de fabrication.",
            ["Créez un ordre de production depuis une nomenclature.", "Allouez les composants.", "Libérez vers le Terminal de production."],
            ["Désallouez les composants avant assemblage pour rendre la matière à l’ATP.", "Après assemblage, inversez par écritures compensatoires — n’effacez pas l’historique."],
            ["Verrouille les composants hors du picking ventes.", "Les feuilles de temps du terminal attachent le coût à l’ordre."],
        ),
    },
    "manufacturing_terminal": {
        "en": pb(
            "Production Terminal",
            "Floor assembly station — start/stop labor, consume components, mint finished-goods labels.",
            "Floor assembly station — start/stop labor, consume components, mint finished goods labels.",
            "Scan the work order, start the timesheet, complete the run, then stop and post assemble.",
            "Shop-floor terminal scan steps for production.",
            ["Scan the work order and Start timesheet.", "Complete the assembly run.", "Stop timesheet and post assemble."],
            ["Stop a timesheet without assemble if the run aborts.", "Wrong assemble: a manager posts a compensating assembly or stock correction."],
            ["Finished goods become allocatable for sales immediately after assemble."],
        ),
        "es": pb(
            "Terminal de producción",
            "Estación de ensamble: inicia/detén mano de obra, consume componentes y genera etiquetas de producto terminado.",
            "Estación de ensamble: inicia/detén mano de obra, consume componentes y genera etiquetas de producto terminado.",
            "Escanea la orden, inicia la hoja de tiempo, completa la corrida y publica el ensamble.",
            "Pasos de escaneo del terminal de piso para producción.",
            ["Escanea la orden de trabajo e inicia la hoja de tiempo.", "Completa la corrida de ensamble.", "Detén la hoja de tiempo y publica el ensamble."],
            ["Detén la hoja de tiempo sin ensamblar si la corrida se aborta.", "Ensamble erróneo: un gerente publica un ensamble o corrección compensatoria."],
            ["El producto terminado queda asignable a ventas de inmediato."],
        ),
        "fr": pb(
            "Terminal de production",
            "Poste d’assemblage — démarrez/arrêtez la main-d’œuvre, consommez les composants, générez les étiquettes de produits finis.",
            "Poste d’assemblage — démarrez/arrêtez la main-d’œuvre, consommez les composants, générez les étiquettes de produits finis.",
            "Scannez l’ordre, démarrez la feuille de temps, terminez la série, puis postez l’assemblage.",
            "Étapes de scan du terminal atelier pour la production.",
            ["Scannez l’ordre et démarrez la feuille de temps.", "Terminez la série d’assemblage.", "Arrêtez la feuille de temps et postez l’assemblage."],
            ["Arrêtez la feuille de temps sans assembler si la série est abandonnée.", "Mauvais assemblage : un manager poste un assemblage ou une correction compensatoire."],
            ["Les produits finis deviennent allouables aux ventes immédiatement."],
        ),
    },
    "issue_supplies": {
        "en": pb(
            "Issue Supplies",
            "Internal consumption against a cost center — deducts stockroom qty without a customer sales order.",
            "Internal consumption against a cost center — deducts stockroom qty without creating a customer SO.",
            "Select the cost center, scan the supply SKU, and confirm the issue.",
            "Internal issue of supplies to cost centers or jobs.",
            ["Select the cost center.", "Scan the supply SKU and confirm issue."],
            ["Before confirm, cancel the issue.", "After confirm, reverse with a positive stock correction to the stockroom attributed to the manager."],
            ["Charges the cost center budget; does not affect customer allocations."],
        ),
        "es": pb(
            "Emitir suministros",
            "Consumo interno contra un centro de costo: descuenta almacén sin un pedido de cliente.",
            "Consumo interno contra un centro de costo: descuenta almacén sin crear un pedido de cliente.",
            "Elige el centro de costo, escanea el SKU y confirma la emisión.",
            "Emisión interna de suministros a centros de costo o trabajos.",
            ["Selecciona el centro de costo.", "Escanea el SKU de suministro y confirma la emisión."],
            ["Antes de confirmar, cancela la emisión.", "Después de confirmar, revierte con una corrección positiva al almacén a nombre del gerente."],
            ["Carga el presupuesto del centro de costo; no afecta asignaciones de clientes."],
        ),
        "fr": pb(
            "Sortir des fournitures",
            "Consommation interne sur un centre de coûts — déduit le magasin sans commande client.",
            "Consommation interne sur un centre de coûts — déduit le magasin sans créer de commande client.",
            "Choisissez le centre de coûts, scannez le SKU et confirmez la sortie.",
            "Sortie interne de fournitures vers des centres de coûts ou chantiers.",
            ["Sélectionnez le centre de coûts.", "Scannez le SKU et confirmez la sortie."],
            ["Avant confirmation, annulez la sortie.", "Après confirmation, inversez par une correction positive au magasin au nom du manager."],
            ["Impute le budget du centre de coûts ; n’affecte pas les allocations clients."],
        ),
    },
    "field_truck": {
        "en": pb(
            "Technician Truck",
            "Consume van stock on-site; low stock signals depot replenishment.",
            "Consume van stock (vehicle location) on-site; low stock signals depot replenishment.",
            "Scan components used on the job and confirm consumption from the assigned vehicle location.",
            "Stock assigned to service trucks and field techs.",
            ["Scan components used on the job.", "Confirm consumption from the assigned vehicle location.", "Work offline if needed — queue replays on reconnect."],
            ["Undo window before offline queue commit.", "After sync, reverse via depot stock correction or transfer back onto the van."],
            ["Reorder-point triggers truck replenishment from the warehouse."],
        ),
        "es": pb(
            "Camión técnico",
            "Consume stock de la furgoneta en sitio; el stock bajo pide reposición al depósito.",
            "Consume stock de la furgoneta (ubicación vehículo) en sitio; el stock bajo pide reposición al depósito.",
            "Escanea componentes usados en el trabajo y confirma el consumo desde la ubicación del vehículo.",
            "Stock asignado a camiones de servicio y técnicos de campo.",
            ["Escanea componentes usados en el trabajo.", "Confirma el consumo desde la ubicación del vehículo.", "Trabaja sin conexión si hace falta: la cola se reenvía al reconectar."],
            ["Ventana de deshacer antes de confirmar la cola offline.", "Tras sincronizar, revierte con una corrección o traslado de vuelta a la furgoneta."],
            ["El punto de reorden dispara la reposición del camión desde el almacén."],
        ),
        "fr": pb(
            "Camion technicien",
            "Consommez le stock du fourgon sur site ; un stock bas signale un réappro dépôt.",
            "Consommez le stock du fourgon (emplacement véhicule) sur site ; un stock bas signale un réappro dépôt.",
            "Scannez les composants utilisés et confirmez la consommation depuis l’emplacement véhicule.",
            "Stock affecté aux camions de service et techniciens terrain.",
            ["Scannez les composants utilisés sur le chantier.", "Confirmez la consommation depuis l’emplacement véhicule.", "Travaillez hors ligne si besoin — la file rejoue à la reconnexion."],
            ["Fenêtre d’annulation avant validation de la file hors ligne.", "Après sync, inversez par correction dépôt ou transfert vers le fourgon."],
            ["Le point de commande déclenche le réappro du camion depuis l’entrepôt."],
        ),
    },
    "reports": {
        "en": pb(
            "Reports",
            "Financial and operational analytics over warehouse data in your access scope.",
            "Financial and operational analytics (profit, COGS, turns) over warehouse data in your access scope.",
            "Open the analysis board you need, filter by date or warehouse, then export for leadership packs.",
            "Saved operational reports and KPI snapshots.",
            ["Open the analysis board you need.", "Filter by date / warehouse.", "Export or screenshot for leadership packs."],
            ["Reports are read-only — reverse underlying transactions on operational pages."],
            ["Headline KPIs may refresh on a short delay rather than updating every second."],
        ),
        "es": pb(
            "Informes",
            "Analítica financiera y operativa sobre datos de almacén en tu alcance de acceso.",
            "Analítica financiera y operativa (margen, COGS, rotación) sobre datos de almacén en tu alcance.",
            "Abre el tablero que necesitas, filtra por fecha o almacén y exporta para dirección.",
            "Informes operativos guardados e instantáneas de KPI.",
            ["Abre el tablero de análisis que necesitas.", "Filtra por fecha / almacén.", "Exporta o captura para paquetes de dirección."],
            ["Los informes son de solo lectura — revierte transacciones en las páginas operativas."],
            ["Los KPI principales pueden actualizarse con un breve retraso."],
        ),
        "fr": pb(
            "Rapports",
            "Analytique financière et opérationnelle sur les données d’entrepôt dans votre périmètre.",
            "Analytique financière et opérationnelle (marge, COGS, rotations) sur les données d’entrepôt dans votre périmètre.",
            "Ouvrez le tableau voulu, filtrez par date ou entrepôt, puis exportez pour la direction.",
            "Rapports opérationnels enregistrés et instantanés KPI.",
            ["Ouvrez le tableau d’analyse voulu.", "Filtrez par date / entrepôt.", "Exportez ou capturez pour les packs direction."],
            ["Les rapports sont en lecture seule — inversez les transactions sur les pages opérationnelles."],
            ["Les KPI principaux peuvent se rafraîchir avec un léger délai."],
        ),
    },
}


def settings_tab(key, en, es, fr):
    REMAINING[key] = {"en": en, "es": es, "fr": fr}


settings_tab(
    "settings_tab_profile",
    pb("Settings — Profile", "Your user profile and the default organization name.", "Your user profile and the default organization name shown across the app.", "Update display name, contact, and locale preferences, then save.", "Your signed-in profile details.", ["Update display name, contact, and locale preferences.", "Save — changes apply to your session immediately."], ["Edit the fields again; profile edits do not touch inventory or roles."], ["Org branding may also appear on documents from the Documents tab."]),
    pb("Ajustes — Perfil", "Tu perfil de usuario y el nombre de organización por defecto.", "Tu perfil de usuario y el nombre de organización mostrado en la app.", "Actualiza nombre, contacto e idioma, luego guarda.", "Datos de tu perfil con sesión iniciada.", ["Actualiza nombre, contacto e idioma.", "Guarda: los cambios aplican de inmediato."], ["Vuelve a editar; el perfil no toca inventario ni roles."], ["La marca de la organización también puede aparecer en documentos."]),
    pb("Paramètres — Profil", "Votre profil utilisateur et le nom d’organisation par défaut.", "Votre profil utilisateur et le nom d’organisation affiché dans l’app.", "Mettez à jour nom, contact et langue, puis enregistrez.", "Détails de votre profil connecté.", ["Mettez à jour nom, contact et langue.", "Enregistrez — les changements s’appliquent tout de suite."], ["Modifiez à nouveau ; le profil ne touche ni stock ni rôles."], ["La marque d’organisation peut aussi apparaître sur les documents."]),
)
settings_tab(
    "settings_tab_users",
    pb("Settings — Users", "Invite users, assign roles, and scope warehouse access.", "Invite and manage company users, assign roles, and scope which warehouses each person can access.", "Invite a user, assign roles that match their job, check warehouses they may access, then save.", "User invitations and role assignments.", ["Invite a user or open an existing account.", "Assign one or more roles that match their job.", "Check the warehouses they may access.", "Save — the next login enforces the new capabilities."], ["Remove a role or warehouse checkbox and save again.", "Deactivate rather than delete users tied to historical stock movements."], ["Warehouse assignments control which bins, waves, and documents appear."]),
    pb("Ajustes — Usuarios", "Invita usuarios, asigna roles y limita el acceso a almacenes.", "Invita y gestiona usuarios, asigna roles y define qué almacenes puede ver cada persona.", "Invita a un usuario, asigna roles, marca almacenes y guarda.", "Invitaciones de usuarios y asignación de roles.", ["Invita a un usuario o abre una cuenta existente.", "Asigna uno o más roles según su trabajo.", "Marca los almacenes a los que puede acceder.", "Guarda: el próximo inicio de sesión aplica las capacidades."], ["Quita un rol o almacén y vuelve a guardar.", "Desactiva en lugar de borrar usuarios ligados a movimientos históricos."], ["Las asignaciones de almacén controlan qué ubicaciones, oleadas y documentos aparecen."]),
    pb("Paramètres — Utilisateurs", "Invitez des utilisateurs, attribuez des rôles et limitez l’accès aux entrepôts.", "Invitez et gérez les utilisateurs, attribuez des rôles et définissez les entrepôts accessibles.", "Invitez un utilisateur, attribuez des rôles, cochez les entrepôts, puis enregistrez.", "Invitations utilisateurs et attributions de rôles.", ["Invitez un utilisateur ou ouvrez un compte existant.", "Attribuez un ou plusieurs rôles selon le poste.", "Cochez les entrepôts accessibles.", "Enregistrez — la prochaine connexion applique les droits."], ["Retirez un rôle ou un entrepôt et enregistrez.", "Désactivez plutôt que supprimer les utilisateurs liés à l’historique stock."], ["Les affectations d’entrepôt contrôlent bacs, vagues et documents visibles."]),
)
settings_tab(
    "settings_tab_warehouses",
    pb("Settings — Warehouses", "Define buildings, zones, and bins used by access and putaway rules.", "Define buildings, zones, and bins that warehouse access and putaway rules reference.", "Add or edit a warehouse, maintain zones/bins, and assign users from the Users tab.", "Warehouses and bin locations for your company.", ["Add or edit a warehouse.", "Maintain zones/bins (or open the visualizer).", "Assign users to the warehouse from the Users tab."], ["Deactivate unused warehouses instead of deleting ones with ledger history.", "Bin coordinate edits do not reverse stock."], ["Pick pathing, RTLS, and replenishment depend on this layout."]),
    pb("Ajustes — Almacenes", "Define edificios, zonas y ubicaciones usados por acceso y ubicación.", "Define edificios, zonas y ubicaciones que usan las reglas de acceso y putaway.", "Añade o edita un almacén, mantén zonas/ubicaciones y asigna usuarios en Usuarios.", "Almacenes y ubicaciones de tu empresa.", ["Añade o edita un almacén.", "Mantén zonas/ubicaciones (o abre el visualizador).", "Asigna usuarios al almacén desde Usuarios."], ["Desactiva almacenes sin uso en lugar de borrar los que tienen historial.", "Editar coordenadas no revierte stock."], ["El ruteo de picking, RTLS y reposición dependen de este layout."]),
    pb("Paramètres — Entrepôts", "Définissez bâtiments, zones et emplacements utilisés par l’accès et le rangement.", "Définissez bâtiments, zones et emplacements référencés par l’accès et le putaway.", "Ajoutez ou modifiez un entrepôt, maintenez zones/emplacements, et affectez les utilisateurs.", "Entrepôts et emplacements de votre société.", ["Ajoutez ou modifiez un entrepôt.", "Maintenez zones/emplacements (ou ouvrez le visualiseur).", "Affectez les utilisateurs depuis l’onglet Utilisateurs."], ["Désactivez les entrepôts inutilisés plutôt que supprimer ceux avec historique.", "Modifier les coordonnées n’inverse pas le stock."], ["Le chemin de picking, RTLS et réappro dépendent de ce layout."]),
)
settings_tab(
    "settings_tab_inventory",
    pb("Settings — Inventory Rules", "Reorder points, UOM defaults, and inventory policy knobs.", "Reorder points, UOM defaults, and inventory policy knobs that feed ATP and replenishment.", "Adjust reorder / safety-stock defaults, then save so planning picks up the new thresholds.", "Inventory policy settings (reorder rules, units, and similar).", ["Adjust reorder / safety-stock defaults as needed.", "Save — planning and low-stock KPIs pick up the new thresholds."], ["Restore prior thresholds with another save."], ["Dashboard Low Stock Count and purchase suggestions use these thresholds."]),
    pb("Ajustes — Reglas de inventario", "Puntos de reorden, unidades y políticas de inventario.", "Puntos de reorden, unidades y políticas que alimentan ATP y reposición.", "Ajusta reorden / stock de seguridad y guarda para que la planificación use los umbrales.", "Ajustes de política de inventario (reorden, unidades y similares).", ["Ajusta reorden / stock de seguridad.", "Guarda: la planificación y el KPI de stock bajo usan los umbrales."], ["Restaura umbrales previos con otro guardado."], ["El conteo de stock bajo del panel y las sugerencias de compra usan estos umbrales."]),
    pb("Paramètres — Règles de stock", "Points de commande, unités et politiques d’inventaire.", "Points de commande, unités et politiques qui alimentent l’ATP et le réappro.", "Ajustez réappro / stock de sécurité, puis enregistrez pour que la planification prenne les seuils.", "Paramètres de politique stock (réappro, unités, etc.).", ["Ajustez réappro / stock de sécurité.", "Enregistrez — planification et KPI stock bas prennent les seuils."], ["Restaurez les seuils précédents par un nouvel enregistrement."], ["Le compteur stock bas du tableau de bord et les suggestions d’achat utilisent ces seuils."]),
)
settings_tab(
    "settings_tab_documents",
    pb("Settings — Documents", "Templates and numbering for POs, packing slips, invoices, and other printables.", "Templates and numbering for POs, packing slips, invoices, and other printable documents.", "Pick the document type, update logo or number series, then save.", "Printable labels and document templates.", ["Pick the document type to customize.", "Update logo, footer, or number series.", "Save — next print jobs use the new template."], ["Revert template fields and save again; already-printed PDFs are not rewritten."], ["Sales ship and PO submit flows render from these templates."]),
    pb("Ajustes — Documentos", "Plantillas y numeración de OC, packing slips, facturas y otros impresos.", "Plantillas y numeración de OC, packing slips, facturas y otros documentos imprimibles.", "Elige el tipo de documento, actualiza logo o serie y guarda.", "Etiquetas y plantillas de documentos imprimibles.", ["Elige el tipo de documento a personalizar.", "Actualiza logo, pie o serie de números.", "Guarda: los próximos trabajos de impresión usan la plantilla."], ["Revierte campos y guarda de nuevo; los PDF ya impresos no se reescriben."], ["Envío de ventas y envío de OC usan estas plantillas."]),
    pb("Paramètres — Documents", "Modèles et numérotation des BC, bons de livraison, factures et autres imprimés.", "Modèles et numérotation des BC, bons de livraison, factures et autres documents imprimables.", "Choisissez le type, mettez à jour logo ou série, puis enregistrez.", "Étiquettes et modèles de documents imprimables.", ["Choisissez le type de document à personnaliser.", "Mettez à jour logo, pied ou série.", "Enregistrez — les prochains travaux d’impression utilisent le modèle."], ["Rétablissez les champs et enregistrez ; les PDF déjà imprimés ne sont pas réécrits."], ["Expédition ventes et soumission BC s’appuient sur ces modèles."]),
)
settings_tab(
    "settings_tab_security",
    pb("Settings — Security & SSO", "SSO, session policy, and authentication hardening for the tenant.", "SSO configuration, session policy, and authentication hardening for the tenant.", "Configure SSO if your IdP is ready, review session toggles, then save.", "Sign-in and security options for your company.", ["Configure SSO provider fields if your IdP is ready.", "Review session / MFA related toggles.", "Save — next login follows the new policy."], ["Disable SSO carefully — ensure local login still works for admins.", "Every security change is audited."], ["Affects how owners, admins, and pickers sign in; does not change warehouse access by itself."]),
    pb("Ajustes — Seguridad y SSO", "SSO, política de sesión y refuerzo de autenticación del inquilino.", "Configuración SSO, política de sesión y refuerzo de autenticación.", "Configura SSO si tu IdP está listo, revisa opciones de sesión y guarda.", "Opciones de inicio de sesión y seguridad de tu empresa.", ["Configura el proveedor SSO si tu IdP está listo.", "Revisa opciones de sesión / MFA.", "Guarda: el próximo inicio de sesión sigue la nueva política."], ["Desactiva SSO con cuidado: asegúrate de que el login local siga funcionando.", "Todo cambio de seguridad queda auditado."], ["Afecta cómo inician sesión propietarios, admins y pickers; no cambia el acceso a almacenes por sí solo."]),
    pb("Paramètres — Sécurité et SSO", "SSO, politique de session et durcissement d’authentification du locataire.", "Configuration SSO, politique de session et durcissement d’authentification.", "Configurez le SSO si votre IdP est prêt, revue des bascules de session, puis enregistrez.", "Options de connexion et de sécurité de votre société.", ["Configurez le fournisseur SSO si votre IdP est prêt.", "Revue des bascules session / MFA.", "Enregistrez — la prochaine connexion suit la nouvelle politique."], ["Désactivez le SSO avec prudence — le login local doit encore marcher pour les admins.", "Chaque changement de sécurité est audité."], ["Affecte la connexion des propriétaires, admins et préparateurs ; ne change pas l’accès entrepôt à lui seul."]),
)
settings_tab(
    "settings_tab_reconciliation",
    pb("Settings — Reconciliation", "Tools and schedules for reconciling inventory and external books.", "Tools and schedules for reconciling inventory levels, accounting balances, and external sync ledgers.", "Review the last run, trigger a reconcile when finance asks, then investigate mismatches on operational pages.", "Comparisons between this system and connected storefronts.", ["Review the last reconciliation run.", "Trigger or schedule a reconcile when finance asks.", "Investigate mismatches on the linked operational pages."], ["Reconciliation jobs do not delete ledger rows — they report drift for manager stock correction."], ["Pairs with Accounting Sync and Cycle Counts when numbers disagree."]),
    pb("Ajustes — Conciliación", "Herramientas y calendarios para conciliar inventario y libros externos.", "Herramientas y calendarios para conciliar inventario, saldos contables y libros de sync externos.", "Revisa la última corrida, lanza una conciliación si finanzas lo pide e investiga desvíos en páginas operativas.", "Comparaciones entre este sistema y las tiendas conectadas.", ["Revisa la última conciliación.", "Lanza o programa una conciliación si finanzas lo pide.", "Investiga desvíos en las páginas operativas vinculadas."], ["Las conciliaciones no borran filas del libro: reportan desvío para corrección del gerente."], ["Se combina con Sync contable y Conteos cíclicos cuando los números no coinciden."]),
    pb("Paramètres — Réconciliation", "Outils et calendriers pour rapprocher stock et livres externes.", "Outils et calendriers pour rapprocher stocks, soldes comptables et journaux de sync externes.", "Examinez la dernière exécution, lancez une réconciliation si la finance le demande, puis enquêtez sur les écarts.", "Comparaisons entre ce système et les boutiques connectées.", ["Examinez la dernière réconciliation.", "Lancez ou planifiez une réconciliation si la finance le demande.", "Enquêtez sur les écarts dans les pages opérationnelles liées."], ["Les jobs de réconciliation n’effacent pas le grand livre — ils signalent l’écart pour correction manager."], ["S’associe à Sync comptable et Inventaires tournants quand les chiffres divergent."]),
)
settings_tab(
    "settings_tab_accounting",
    pb("Settings — Accounting Sync", "Connect QuickBooks/Xero so invoices and journals flow through finance sync.", "Connect QuickBooks/Xero (or similar) so invoices and journals flow through the finance sync.", "Connect or refresh the accounting adapter, map tax schemes, and retry FAILED rows.", "Accounting export status for finance systems.", ["Connect or refresh the accounting adapter.", "Map tax schemes and accounts as prompted.", "Watch sync status chips for FAILED rows and retry."], ["Disconnecting stops new syncs; already-posted external journals must be voided in the accounting system."], ["Paid invoices and COGS journals depend on this bridge."]),
    pb("Ajustes — Sync contable", "Conecta QuickBooks/Xero para que facturas y diarios fluyan a finanzas.", "Conecta QuickBooks/Xero (o similar) para que facturas y diarios fluyan por el sync de finanzas.", "Conecta o refresca el adaptador, mapea impuestos y reintenta filas FALLIDAS.", "Estado de exportación contable hacia sistemas de finanzas.", ["Conecta o refresca el adaptador contable.", "Mapea esquemas de impuestos y cuentas.", "Vigila chips FALLIDO y reintenta."], ["Desconectar detiene syncs nuevos; los diarios ya publicados se anulan en el sistema contable."], ["Las facturas pagadas y diarios de COGS dependen de este puente."]),
    pb("Paramètres — Sync comptable", "Connectez QuickBooks/Xero pour que factures et journaux transitent vers la finance.", "Connectez QuickBooks/Xero (ou similaire) pour que factures et journaux transitent par le sync finance.", "Connectez ou rafraîchissez l’adaptateur, mappez les taxes, et réessayez les lignes ÉCHOUÉ.", "Statut d’export comptable vers les systèmes finance.", ["Connectez ou rafraîchissez l’adaptateur comptable.", "Mappez schémas de taxe et comptes.", "Surveillez les pastilles ÉCHOUÉ et réessayez."], ["Déconnecter arrête les nouveaux syncs ; les journaux déjà postés se voident dans le système comptable."], ["Les factures payées et journaux de COGS dépendent de ce pont."]),
)
settings_tab(
    "settings_tab_integrations",
    pb("Settings — Integrations", "Connect storefronts and accounting so orders and payments land without double entry.", "Connect e-commerce storefronts and accounting systems so orders and payments land without double entry.", "Choose a connector, paste connection keys, enable the channel, and verify a test order or payment.", "Connections to storefronts and marketplaces.", ["Choose an e-commerce or accounting connector.", "Paste the connection keys provided by the storefront or accounting system.", "Enable the channel and verify a test order or payment event."], ["Disable a connector to stop inbound events; already-imported sales orders stay in the outbound pipeline.", "Rotate connection keys if a key leaks."], ["Storefront connections create or update sales orders that still allocate and ship like office-entered orders."]),
    pb("Ajustes — Integraciones", "Conecta tiendas y contabilidad para que pedidos y pagos lleguen sin doble captura.", "Conecta tiendas e-commerce y sistemas contables para que pedidos y pagos lleguen sin doble captura.", "Elige un conector, pega las claves, habilita el canal y verifica un pedido o pago de prueba.", "Conexiones a tiendas y marketplaces.", ["Elige un conector de e-commerce o contabilidad.", "Pega las claves que da la tienda o el sistema contable.", "Habilita el canal y verifica un pedido o pago de prueba."], ["Deshabilita un conector para detener eventos; los pedidos ya importados siguen en la tubería de salida.", "Rota las claves si hay una filtración."], ["Las conexiones de tienda crean o actualizan pedidos que se asignan y envían como los de oficina."]),
    pb("Paramètres — Intégrations", "Connectez boutiques et compta pour que commandes et paiements arrivent sans double saisie.", "Connectez boutiques e-commerce et systèmes comptables pour que commandes et paiements arrivent sans double saisie.", "Choisissez un connecteur, collez les clés, activez le canal et vérifiez une commande ou un paiement test.", "Connexions aux boutiques et marketplaces.", ["Choisissez un connecteur e-commerce ou comptable.", "Collez les clés fournies par la boutique ou le système comptable.", "Activez le canal et vérifiez une commande ou un paiement test."], ["Désactivez un connecteur pour stopper les événements ; les commandes déjà importées restent dans le pipeline sortant.", "Faites tourner les clés en cas de fuite."], ["Les connexions boutique créent ou mettent à jour des commandes qui s’allouent et s’expédient comme celles du bureau."]),
)
settings_tab(
    "settings_tab_mesh",
    pb("Settings — Partner Catalog", "Cross-tenant mappings so seller SKUs resolve to buyer products.", "Cross-tenant mesh mappings so seller SKUs resolve to buyer products on multi-party POs/SOs.", "Open Partner Catalog Mapping, map partner SKUs to local variants, then save.", "Partner catalog sharing between trusted companies.", ["Open Partner Catalog Mapping.", "Map partner SKUs to local variants.", "Save — the mesh uses the map on the next purchase-order submit."], ["Unmap or remap a SKU; historical documents keep the snapshot they were created with."], ["Unmapped mesh lines may create DRAFT exception sales orders for review."]),
    pb("Ajustes — Catálogo de socios", "Mapeos entre inquilinos para que los SKU del vendedor coincidan con productos del comprador.", "Mapeos de malla entre inquilinos para que los SKU del vendedor coincidan en OC/pedidos multipartes.", "Abre Mapeo de catálogo de socios, mapea SKU a variantes locales y guarda.", "Compartir catálogo entre empresas de confianza.", ["Abre Mapeo de catálogo de socios.", "Mapea SKU del socio a variantes locales.", "Guarda: la malla usa el mapa en el próximo envío de OC."], ["Desmapea o remapéa un SKU; los documentos históricos conservan la instantánea original."], ["Líneas de malla sin mapear pueden crear pedidos DRAFT de excepción."]),
    pb("Paramètres — Catalogue partenaires", "Correspondances inter-locataires pour que les SKU vendeur correspondent aux produits acheteur.", "Correspondances mesh inter-locataires pour que les SKU vendeur correspondent sur BC/commandes multi-parties.", "Ouvrez le mapping catalogue partenaire, mappez les SKU vers les variantes locales, puis enregistrez.", "Partage de catalogue entre sociétés de confiance.", ["Ouvrez le mapping catalogue partenaire.", "Mappez les SKU partenaire vers les variantes locales.", "Enregistrez — le mesh utilise la carte au prochain envoi de BC."], ["Dé-mappez ou re-mappez un SKU ; les documents historiques gardent l’instantané d’origine."], ["Les lignes mesh non mappées peuvent créer des commandes DRAFT d’exception."]),
)
settings_tab(
    "settings_tab_operations",
    pb("Settings — Operations", "Floor rules — blind receiving, adjustment limits, scanner options — plus the Audit Log.", "Tenant floor rules — blind receiving, adjustment limits, scanner options — plus the Audit Log of who changed what.", "Toggle the operational rule you need, save, then confirm floor devices pick up the new rule.", "Floor operating rules (receiving and scanner options).", ["Toggle the operational rule you need (e.g. blind receiving, max adjust qty).", "Save — the audit log records the actor.", "Confirm floor devices pick up the new rule on the next scan."], ["Toggle the rule back; every change is recorded in the audit history.", "Raising an adjustment limit does not auto-approve past pending manager review counts."], ["Blind receiving and variance thresholds change what pickers may post without manager review."]),
    pb("Ajustes — Operaciones", "Reglas de piso: recepción ciega, límites de ajuste, opciones de escáner, más el registro de auditoría.", "Reglas de piso del inquilino: recepción ciega, límites de ajuste, opciones de escáner, más el registro de quién cambió qué.", "Activa la regla que necesites, guarda y confirma que los dispositivos la tomen.", "Reglas operativas de piso (recepción y opciones de escáner).", ["Activa la regla operativa (p. ej. recepción ciega, máx. de ajuste).", "Guarda: el registro de auditoría anota al actor.", "Confirma que los dispositivos tomen la regla en el próximo escaneo."], ["Vuelve a desactivar la regla; cada cambio queda en auditoría.", "Subir un límite de ajuste no aprueba conteos pendientes de gerente."], ["Recepción ciega y umbrales de varianza cambian lo que los pickers pueden publicar sin revisión."]),
    pb("Paramètres — Opérations", "Règles sol — réception à l’aveugle, limites d’ajustement, options scanner — plus le journal d’audit.", "Règles sol du locataire — réception à l’aveugle, limites d’ajustement, options scanner — plus le journal de qui a changé quoi.", "Activez la règle voulue, enregistrez, puis confirmez que les appareils la prennent.", "Règles d’exploitation au sol (réception et options scanner).", ["Activez la règle opérationnelle (ex. réception à l’aveugle, max d’ajustement).", "Enregistrez — le journal d’audit note l’acteur.", "Confirmez que les appareils prennent la règle au prochain scan."], ["Remettez la règle ; chaque changement est audité.", "Augmenter une limite n’approuve pas automatiquement les comptages en revue manager."], ["Réception à l’aveugle et seuils d’écart changent ce que les préparateurs peuvent poster sans revue."]),
)
settings_tab(
    "settings_tab_syncConflicts",
    pb("Settings — Sync Conflicts", "Review parked offline floor scans that could not finish after reconnecting.", "Review parked offline floor scans that could not finish after reconnecting.", "Open a parked conflict, correct highlighted fields, then Approve & Re-process or Discard.", "The conflict list on Dashboard or Exceptions.", ["Open a PARKED conflict.", "Correct the highlighted fields if needed.", "Approve & Re-process or Discard."], ["Discard permanently drops the parked scan.", "Approve posts under the manager name; reverse later only with a compensating stock correction."], ["Same board is reachable from Exceptions Sync tab and the dashboard banner."]),
    pb("Ajustes — Conflictos de sync", "Revisa escaneos de piso offline que no pudieron terminar al reconectar.", "Revisa escaneos de piso offline que no pudieron terminar al reconectar.", "Abre un conflicto aparcado, corrige campos resaltados y Aprueba y reprocesa o Descarta.", "La lista de conflictos en Panel o Excepciones.", ["Abre un conflicto APARCADO.", "Corrige los campos resaltados si hace falta.", "Aprueba y reprocesa o Descarta."], ["Descartar elimina de forma permanente el escaneo aparcado.", "Aprobar publica a nombre del gerente; revierte después solo con una corrección compensatoria."], ["El mismo tablero está en Excepciones (sync) y en el banner del panel."]),
    pb("Paramètres — Conflits de sync", "Examinez les scans sol hors ligne qui n’ont pas pu se terminer après reconnexion.", "Examinez les scans sol hors ligne qui n’ont pas pu se terminer après reconnexion.", "Ouvrez un conflit en attente, corrigez les champs, puis Approuver et retraiter ou Jeter.", "La liste des conflits sur Tableau de bord ou Exceptions.", ["Ouvrez un conflit EN ATTENTE.", "Corrigez les champs surlignés si besoin.", "Approuvez et retraitez ou Jetez."], ["Jeter supprime définitivement le scan en attente.", "Approuver poste au nom du manager ; inversez plus tard seulement par correction compensatoire."], ["Le même tableau est accessible depuis Exceptions (sync) et la bannière du tableau de bord."]),
)
settings_tab(
    "settings_tab_costCenters",
    pb("Settings — Cost Centers & Requisitions", "Internal budgets that authorize Issue Supplies without a customer sales order.", "Internal budgets and requisitions that authorize Issue Supplies without a customer sales order.", "Create or edit a cost center, review draft requisitions, then floor Issue Supplies charges the center.", "Cost centers used when issuing internal supplies.", ["Create or edit a cost center budget.", "Review DRAFT requisitions and approve when appropriate.", "Floor Issue Supplies charges against the approved center."], ["Cancel DRAFT requisitions before issue.", "After issue, reverse stock with a manager stock correction referencing the original consumption."], ["Issue Supplies on the floor reads these centers for budget clearance."]),
    pb("Ajustes — Centros de costo y requisiciones", "Presupuestos internos que autorizan Emitir suministros sin un pedido de cliente.", "Presupuestos y requisiciones internas que autorizan Emitir suministros sin un pedido de cliente.", "Crea o edita un centro de costo, revisa requisiciones en borrador y el piso carga al centro.", "Centros de costo usados al emitir suministros internos.", ["Crea o edita el presupuesto de un centro de costo.", "Revisa requisiciones en BORRADOR y aprueba.", "Emitir suministros en el piso carga contra el centro aprobado."], ["Cancela requisiciones en BORRADOR antes de emitir.", "Tras emitir, revierte stock con una corrección del gerente que cite el consumo original."], ["Emitir suministros en el piso lee estos centros para autorización de presupuesto."]),
    pb("Paramètres — Centres de coûts et demandes", "Budgets internes qui autorisent Sortir des fournitures sans commande client.", "Budgets et demandes internes qui autorisent Sortir des fournitures sans commande client.", "Créez ou modifiez un centre de coûts, examinez les demandes brouillon, puis le sol impute le centre.", "Centres de coûts utilisés pour les sorties internes.", ["Créez ou modifiez le budget d’un centre de coûts.", "Examinez les demandes BROUILLON et approuvez.", "Sortir des fournitures au sol impute le centre approuvé."], ["Annulez les demandes BROUILLON avant sortie.", "Après sortie, inversez le stock par une correction manager référençant la consommation."], ["Sortir des fournitures au sol lit ces centres pour le dégagement budgétaire."]),
)
settings_tab(
    "settings_profile",
    pb("Profile settings", "Dedicated profile page for the signed-in user.", "Dedicated profile page for the signed-in user (same domain as Settings → Profile).", "Update personal details, then save and return to the app shell.", "Your signed-in profile details.", ["Update personal details.", "Save and return to the app shell."], ["Edit again; no inventory impact."], ["Opens from the header avatar; Users tab remains the place for role and location access changes."]),
    pb("Ajustes de perfil", "Página de perfil dedicada para el usuario con sesión iniciada.", "Página de perfil dedicada (mismo dominio que Ajustes → Perfil).", "Actualiza datos personales, guarda y vuelve al shell.", "Datos de tu perfil con sesión iniciada.", ["Actualiza datos personales.", "Guarda y vuelve al shell."], ["Vuelve a editar; no hay impacto de inventario."], ["Se abre desde el avatar del encabezado; Usuarios sigue siendo el lugar para roles y almacenes."]),
    pb("Paramètres de profil", "Page de profil dédiée pour l’utilisateur connecté.", "Page de profil dédiée (même domaine que Paramètres → Profil).", "Mettez à jour les détails personnels, enregistrez et revenez au shell.", "Détails de votre profil connecté.", ["Mettez à jour les détails personnels.", "Enregistrez et revenez au shell."], ["Modifiez à nouveau ; aucun impact stock."], ["S’ouvre depuis l’avatar d’en-tête ; l’onglet Utilisateurs reste le lieu des rôles et entrepôts."]),
)
settings_tab(
    "settings_billing",
    pb("Billing", "OWNER-scoped subscription and plan management for the tenant.", "OWNER-scoped subscription and plan management for the tenant.", "Review the current plan and seats, then change plan or payment method in the billing portal when needed.", "Subscription and billing managed by owners.", ["Review the current plan and seats.", "Change plan or payment method in the billing portal when needed."], ["Plan downgrades may take effect at period end — confirm in the billing portal.", "Billing changes do not reverse warehouse transactions."], ["Only OWNER (and sometimes ADMIN) can open this hub."]),
    pb("Facturación", "Suscripción y plan del inquilino, alcance PROPIETARIO.", "Suscripción y plan del inquilino, alcance PROPIETARIO.", "Revisa el plan y asientos, luego cambia plan o método de pago en el portal de facturación.", "Suscripción y facturación gestionadas por propietarios.", ["Revisa el plan actual y los asientos.", "Cambia plan o método de pago en el portal cuando haga falta."], ["Las bajadas de plan pueden aplicar al fin de periodo — confirma en el portal.", "Los cambios de facturación no revierten transacciones de almacén."], ["Solo PROPIETARIO (y a veces ADMIN) puede abrir este hub."]),
    pb("Facturation", "Abonnement et offre du locataire, périmètre PROPRIÉTAIRE.", "Abonnement et offre du locataire, périmètre PROPRIÉTAIRE.", "Examinez l’offre et les sièges, puis changez offre ou moyen de paiement dans le portail de facturation.", "Abonnement et facturation gérés par les propriétaires.", ["Examinez l’offre actuelle et les sièges.", "Changez offre ou moyen de paiement dans le portail si besoin."], ["Les rétrogradations peuvent s’appliquer en fin de période — confirmez dans le portail.", "Les changements de facturation n’inversent pas les transactions d’entrepôt."], ["Seul PROPRIÉTAIRE (parfois ADMIN) peut ouvrir ce hub."]),
)
settings_tab(
    "settings_fintech",
    pb("Cash Flow & Financing", "OWNER-scoped cash-flow and financing insights tied to AR/AP signals.", "OWNER-scoped cash-flow and financing insights tied to AR/AP signals.", "Review cash-flow panels and open financing offers only when OWNER policy allows.", "Payment and payout options configured by owners.", ["Review cash-flow panels.", "Open financing offers only when OWNER policy allows."], ["Financing acceptances are contractual — reverse via the fintech partner, not the inventory ledger."], ["Uses paid invoices and open receivables from the Invoices page."]),
    pb("Flujo de caja y financiamiento", "Perspectivas de caja y financiamiento, alcance PROPIETARIO, ligadas a CXC/CXP.", "Perspectivas de caja y financiamiento, alcance PROPIETARIO, ligadas a CXC/CXP.", "Revisa paneles de caja y abre ofertas de financiamiento solo si la política de PROPIETARIO lo permite.", "Opciones de pago y desembolso configuradas por propietarios.", ["Revisa paneles de flujo de caja.", "Abre ofertas de financiamiento solo si la política de PROPIETARIO lo permite."], ["Aceptar financiamiento es contractual: revierte con el socio fintech, no en el libro de inventario."], ["Usa facturas pagadas y cuentas por cobrar abiertas de Facturas."]),
    pb("Trésorerie et financement", "Insights trésorerie et financement, périmètre PROPRIÉTAIRE, liés aux signaux client/fournisseur.", "Insights trésorerie et financement, périmètre PROPRIÉTAIRE, liés aux signaux client/fournisseur.", "Examinez les panneaux de trésorerie et n’ouvrez des offres de financement que si la politique PROPRIÉTAIRE le permet.", "Options de paiement et de versement configurées par les propriétaires.", ["Examinez les panneaux de trésorerie.", "Ouvrez des offres de financement seulement si la politique PROPRIÉTAIRE le permet."], ["L’acceptation d’un financement est contractuelle — inversez via le partenaire fintech, pas le grand livre stock."], ["S’appuie sur les factures payées et créances ouvertes de Factures."]),
)
settings_tab(
    "settings_integrations",
    pb("Integrations Hub", "Hub that routes into e-commerce, accounting, and operations integration surfaces.", "Hub that routes into e-commerce, accounting, and operations integration surfaces.", "Pick the connector category, then follow the shortcut into the matching Settings tab.", "Integration setup screens for storefronts and partners.", ["Pick the connector category (storefront, accounting, or operations).", "Follow the shortcut into the matching Settings tab or connection page."], ["Disable connectors from the Integrations or Accounting tabs — the hub itself does not mutate stock."], ["Shortcuts land on Integrations, Accounting, or Operations tabs."]),
    pb("Centro de integraciones", "Hub que lleva a superficies de e-commerce, contabilidad y operaciones.", "Hub que lleva a superficies de e-commerce, contabilidad y operaciones.", "Elige la categoría del conector y sigue el atajo a la pestaña de Ajustes.", "Pantallas de alta de integraciones para tiendas y socios.", ["Elige la categoría (tienda, contabilidad u operaciones).", "Sigue el atajo a la pestaña o página de conexión."], ["Deshabilita conectores desde Integraciones o Contabilidad: el hub no muta stock."], ["Los atajos llegan a Integraciones, Contabilidad u Operaciones."]),
    pb("Centre d’intégrations", "Hub vers les surfaces e-commerce, comptable et opérations.", "Hub vers les surfaces e-commerce, comptable et opérations.", "Choisissez la catégorie de connecteur, puis suivez le raccourci vers l’onglet Paramètres.", "Écrans de configuration d’intégration pour boutiques et partenaires.", ["Choisissez la catégorie (boutique, compta ou opérations).", "Suivez le raccourci vers l’onglet ou la page de connexion."], ["Désactivez les connecteurs depuis Intégrations ou Compta — le hub ne mute pas le stock."], ["Les raccourcis mènent aux onglets Intégrations, Compta ou Opérations."]),
)
settings_tab(
    "import",
    pb("Import wizard", "Bulk-load products via mapped CSV/Excel with preflight validation.", "Bulk-load products/variants via mapped CSV/Excel with preflight validation.", "Download the template, map columns, run preflight, then commit the import.", "Spreadsheets uploaded by your team to create or update records.", ["Download the template.", "Map columns and run preflight.", "Resolve missing products, then commit the import."], ["Stop before commit if preflight shows errors.", "After commit, correct with a follow-up import or manual edits — imports do not delete ledger stock."], ["Imported masters immediately feed PO/SO and floor barcode resolution."]),
    pb("Asistente de importación", "Carga masiva de productos vía CSV/Excel mapeado con validación previa.", "Carga masiva de productos/variantes vía CSV/Excel mapeado con validación previa.", "Descarga la plantilla, mapea columnas, ejecuta preflight y confirma la importación.", "Hojas de cálculo que sube tu equipo para crear o actualizar registros.", ["Descarga la plantilla.", "Mapea columnas y ejecuta preflight.", "Resuelve productos faltantes y confirma la importación."], ["Detente antes de confirmar si el preflight muestra errores.", "Tras confirmar, corrige con otra importación o ediciones: las importaciones no borran stock del libro."], ["Los maestros importados alimentan de inmediato OC/pedidos y códigos de barras de piso."]),
    pb("Assistant d’import", "Chargement en masse de produits via CSV/Excel mappé avec précontrôle.", "Chargement en masse de produits/variantes via CSV/Excel mappé avec précontrôle.", "Téléchargez le modèle, mappez les colonnes, lancez le précontrôle, puis validez l’import.", "Tableurs téléversés par votre équipe pour créer ou mettre à jour des fiches.", ["Téléchargez le modèle.", "Mappez les colonnes et lancez le précontrôle.", "Résolvez les produits manquants, puis validez l’import."], ["Arrêtez avant validation si le précontrôle montre des erreurs.", "Après validation, corrigez par un import de suivi ou des éditions — l’import n’efface pas le stock du grand livre."], ["Les masters importés alimentent immédiatement BC/commandes et la résolution codes-barres au sol."]),
)
settings_tab(
    "showroom",
    pb("B2B Showroom", "Customer portal for catalog, cart, checkout, and order status at negotiated prices.", "Customer portal for catalog browse, cart, checkout, and order status at negotiated prices.", "Browse the restricted catalog, add to cart, checkout, then track order status.", "Products your B2B customers can browse in the showroom.", ["Browse the restricted catalog.", "Add to cart and checkout.", "Track order status under Showroom Orders."], ["Remove cart lines before checkout.", "After place-order, cancellations go through the office sales-order Cancel/Un-allocate path."], ["Portal DRAFT orders enter the same outbound pipeline as office-entered sales orders."]),
    pb("Showroom B2B", "Portal de cliente para catálogo, carrito, checkout y estado de pedidos a precios negociados.", "Portal de cliente para catálogo, carrito, checkout y estado de pedidos a precios negociados.", "Explora el catálogo restringido, añade al carrito, paga y sigue el estado del pedido.", "Productos que tus clientes B2B pueden ver en el showroom.", ["Explora el catálogo restringido.", "Añade al carrito y paga.", "Sigue el estado del pedido en Pedidos del showroom."], ["Quita líneas del carrito antes del checkout.", "Tras colocar el pedido, las cancelaciones van por Cancelar/Desasignar en oficina."], ["Los pedidos DRAFT del portal entran a la misma tubería de salida que los de oficina."]),
    pb("Showroom B2B", "Portail client pour catalogue, panier, paiement et statut de commande aux prix négociés.", "Portail client pour catalogue, panier, paiement et statut de commande aux prix négociés.", "Parcourez le catalogue restreint, ajoutez au panier, payez, puis suivez le statut.", "Produits que vos clients B2B peuvent parcourir dans le showroom.", ["Parcourez le catalogue restreint.", "Ajoutez au panier et payez.", "Suivez le statut sous Commandes showroom."], ["Retirez des lignes du panier avant paiement.", "Après commande, les annulations passent par Annuler/Désallouer au bureau."], ["Les commandes DRAFT du portail rejoignent le même pipeline sortant que celles saisies au bureau."]),
)


def playbooks_for(lang: str) -> dict:
    out: dict = {}
    for key, langs in EXISTING_EXTRAS.items():
        out[key] = langs[lang]
    for key, langs in REMAINING.items():
        out[key] = langs[lang]
    return out


def main() -> None:
    for lang in ("en", "es", "fr"):
        path = ROOT / f"{lang}.json"
        data = json.loads(path.read_text(encoding="utf-8"))
        extra = {
            "chat": CHAT[lang],
            "roles": ROLES[lang],
            "profile": PROFILE[lang],
            "pageHelp": {"playbooks": playbooks_for(lang)},
        }
        merged = deep_merge(data, extra)
        path.write_text(json.dumps(merged, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"wrote {path} playbooks={len(merged['pageHelp']['playbooks'])}")


if __name__ == "__main__":
    main()
