let pendingFile: File | null = null;

export function stashApInvoiceFile(file: File) {
  pendingFile = file;
}

export function takeApInvoiceFile(): File | null {
  const file = pendingFile;
  pendingFile = null;
  return file;
}
