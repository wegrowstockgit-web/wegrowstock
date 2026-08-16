export type PosLanguage = 'en' | 'es' | 'fr';

const DICTIONARY = {
  en: {
    'app.back': 'Back to register',
    'login.title': 'Retail POS sign-in',
    'login.subtitle': 'Same tenant credentials as the WMS. The register stays usable offline.',
    'login.email': 'Email',
    'login.password': 'Password',
    'login.submit': 'Open register',
    'login.error': 'Invalid email or password',
    'login.failed': 'Login failed',
    'register.scanLabel': 'Barcode / UPC',
    'register.scanPlaceholder': 'Scan barcode / UPC',
    'register.add': 'Add',
    'register.item': 'Item',
    'register.upc': 'UPC',
    'register.unitPrice': 'Unit price',
    'register.qty': 'Qty',
    'register.lineTotal': 'Line total',
    'register.empty': 'Scan a barcode to start the sale',
    'register.checkout': 'Checkout',
    'register.subtotal': 'Subtotal',
    'register.grandTotal': 'Grand total',
    'register.taxUs': 'USA sales tax (8.25%)',
    'register.taxMx': 'Mexico IVA (16%)',
    'register.taxUsShort': 'USA',
    'register.taxMxShort': 'Mexico',
    'register.exactCash': 'Exact cash',
    'register.card': 'Card / terminal',
    'register.backspace': 'Backspace',
    'register.success': 'SUCCESS - NEXT CUSTOMER',
    'register.unknownUpc': 'Unknown UPC {upc}',
    'register.scanFirst': 'Scan an item first',
    'register.decrease': 'Decrease {name}',
    'register.increase': 'Increase {name}',
    'register.signIn': 'Sign in',
    'register.offline': 'Offline',
    'register.online': 'Ready',
    'register.placeHint': 'Opened in {place}',
    'register.currencyMismatch': 'WMS {wms} · local {place}',
    'locked.title': 'Retail POS is not enabled',
    'locked.body':
      'This workspace or subscription tier does not include Retail POS. Ask an owner to enable the module in WMS before taking sales.',
    'locked.signin': 'Sign in with another account',
  },
  es: {
    'app.back': 'Volver a la caja',
    'login.title': 'Acceso al POS',
    'login.subtitle': 'Las mismas credenciales del WMS. La caja sigue funcionando sin conexión.',
    'login.email': 'Correo',
    'login.password': 'Contraseña',
    'login.submit': 'Abrir caja',
    'login.error': 'Correo o contraseña no válidos',
    'login.failed': 'Error de acceso',
    'register.scanLabel': 'Código de barras / UPC',
    'register.scanPlaceholder': 'Escanear código / UPC',
    'register.add': 'Añadir',
    'register.item': 'Artículo',
    'register.upc': 'UPC',
    'register.unitPrice': 'Precio',
    'register.qty': 'Cant.',
    'register.lineTotal': 'Importe',
    'register.empty': 'Escanea un código para iniciar la venta',
    'register.checkout': 'Cobro',
    'register.subtotal': 'Subtotal',
    'register.grandTotal': 'Total',
    'register.taxUs': 'Impuesto USA (8.25%)',
    'register.taxMx': 'IVA México (16%)',
    'register.taxUsShort': 'USA',
    'register.taxMxShort': 'México',
    'register.exactCash': 'Efectivo exacto',
    'register.card': 'Tarjeta / terminal',
    'register.backspace': 'Borrar',
    'register.success': 'ÉXITO — SIGUIENTE CLIENTE',
    'register.unknownUpc': 'UPC desconocido {upc}',
    'register.scanFirst': 'Escanea un artículo primero',
    'register.decrease': 'Quitar {name}',
    'register.increase': 'Añadir {name}',
    'register.signIn': 'Entrar',
    'register.offline': 'Sin conexión',
    'register.online': 'Listo',
    'register.placeHint': 'Abierto en {place}',
    'register.currencyMismatch': 'WMS {wms} · local {place}',
    'locked.title': 'El POS no está activado',
    'locked.body':
      'Este espacio o plan no incluye Retail POS. Pide a un propietario que active el módulo en el WMS antes de cobrar.',
    'locked.signin': 'Entrar con otra cuenta',
  },
  fr: {
    'app.back': 'Retour à la caisse',
    'login.title': 'Connexion POS',
    'login.subtitle': 'Les mêmes identifiants que le WMS. La caisse reste utilisable hors ligne.',
    'login.email': 'E-mail',
    'login.password': 'Mot de passe',
    'login.submit': 'Ouvrir la caisse',
    'login.error': 'E-mail ou mot de passe invalide',
    'login.failed': 'Échec de connexion',
    'register.scanLabel': 'Code-barres / UPC',
    'register.scanPlaceholder': 'Scanner le code / UPC',
    'register.add': 'Ajouter',
    'register.item': 'Article',
    'register.upc': 'UPC',
    'register.unitPrice': 'Prix',
    'register.qty': 'Qté',
    'register.lineTotal': 'Ligne',
    'register.empty': 'Scannez un code pour commencer la vente',
    'register.checkout': 'Encaissement',
    'register.subtotal': 'Sous-total',
    'register.grandTotal': 'Total',
    'register.taxUs': 'Taxe USA (8,25 %)',
    'register.taxMx': 'TVA Mexique (16 %)',
    'register.taxUsShort': 'USA',
    'register.taxMxShort': 'Mexique',
    'register.exactCash': 'Espèces exactes',
    'register.card': 'Carte / terminal',
    'register.backspace': 'Retour',
    'register.success': 'SUCCÈS — CLIENT SUIVANT',
    'register.unknownUpc': 'UPC inconnu {upc}',
    'register.scanFirst': 'Scannez un article d’abord',
    'register.decrease': 'Retirer {name}',
    'register.increase': 'Ajouter {name}',
    'register.signIn': 'Connexion',
    'register.offline': 'Hors ligne',
    'register.online': 'Prêt',
    'register.placeHint': 'Ouvert à {place}',
    'register.currencyMismatch': 'WMS {wms} · local {place}',
    'locked.title': 'Le POS n’est pas activé',
    'locked.body':
      'Cet espace ou cet abonnement n’inclut pas le POS. Demandez à un propriétaire d’activer le module dans le WMS avant d’encaisser.',
    'locked.signin': 'Se connecter avec un autre compte',
  },
} as const;

export type PosMessageKey = keyof (typeof DICTIONARY)['en'];

export function normalizePosLanguage(raw?: string | null): PosLanguage {
  if (!raw) return 'en';
  const token = raw.trim().toLowerCase().replace('_', '-');
  if (token.startsWith('es')) return 'es';
  if (token.startsWith('fr')) return 'fr';
  return 'en';
}

export function translate(
  language: PosLanguage,
  key: PosMessageKey,
  vars?: Record<string, string>,
): string {
  const table = DICTIONARY[language] ?? DICTIONARY.en;
  let text: string = table[key] ?? DICTIONARY.en[key] ?? key;
  if (vars) {
    for (const [name, value] of Object.entries(vars)) {
      text = text.replaceAll(`{${name}}`, value);
    }
  }
  return text;
}
