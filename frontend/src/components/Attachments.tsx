import { useRef } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { FileText, ImageIcon, Paperclip, Trash2 } from 'lucide-react'
import { toast } from 'sonner'
import { api, tokenStore, ApiError } from '../lib/api'
import type { AttachmentInfo } from '../lib/types'

export const ACCEPT = 'application/pdf,image/jpeg,image/png'
export const MAX_MB = 5

export async function uploadAttachment(requestId: number, file: File) {
  if (file.size > MAX_MB * 1024 * 1024) throw new ApiError(400, `Il file supera i ${MAX_MB} MB`)
  const fd = new FormData()
  fd.append('file', file)
  const token = tokenStore.get()
  const res = await fetch(`/api/requests/${requestId}/attachments`, {
    method: 'POST', body: fd, headers: token ? { Authorization: `Bearer ${token}` } : {},
  })
  if (!res.ok) {
    let msg = 'Caricamento non riuscito'
    try { const p = await res.json(); if (p?.detail) msg = p.detail } catch { /* ignore */ }
    throw new ApiError(res.status, msg)
  }
  return res.json() as Promise<AttachmentInfo>
}

const kb = (n: number) => n < 1024 * 1024 ? `${Math.max(1, Math.round(n / 1024))} KB` : `${(n / 1024 / 1024).toFixed(1)} MB`

/** Elenco allegati di una richiesta; il titolare puo' aggiungerne e toglierli. */
export function Attachments({ requestId, items, editable }: { requestId: number; items: AttachmentInfo[]; editable?: boolean }) {
  const qc = useQueryClient()
  const input = useRef<HTMLInputElement>(null)
  const add = useMutation({
    mutationFn: (f: File) => uploadAttachment(requestId, f),
    onSuccess: () => { toast.success('Allegato caricato'); qc.invalidateQueries() },
    onError: (e: Error) => toast.error(e.message),
  })
  const remove = useMutation({
    mutationFn: (id: number) => api(`/api/attachments/${id}`, { method: 'DELETE' }),
    onSuccess: () => { toast.success('Allegato rimosso'); qc.invalidateQueries() },
    onError: (e: Error) => toast.error(e.message),
  })
  const open = async (id: number) => {
    // Apriamo subito la finestra (evita il blocco popup), poi le diamo il link firmato.
    const w = window.open('', '_blank')
    try {
      const { url } = await api<{ url: string }>(`/api/attachments/${id}/link`)
      if (w) w.location.href = url; else window.location.href = url
    } catch (e) { w?.close(); toast.error((e as Error).message) }
  }
  if (!items.length && !editable) return null
  return (
    <div className="mt-2 flex flex-wrap items-center gap-2">
      {items.map(a => (
        <span key={a.id} className="inline-flex items-center gap-1.5 rounded-lg border border-soft px-2 py-1 text-xs">
          {a.contentType === 'application/pdf' ? <FileText size={13} /> : <ImageIcon size={13} />}
          <button className="max-w-[180px] truncate hover:underline" onClick={() => open(a.id)} title={a.filename}>{a.filename}</button>
          <span className="text-muted">{kb(a.sizeBytes)}</span>
          {editable && <button aria-label="Rimuovi allegato" className="text-muted hover:text-rose-500" onClick={() => remove.mutate(a.id)}><Trash2 size={13} /></button>}
        </span>
      ))}
      {editable && items.length < 5 && (
        <>
          <button className="inline-flex items-center gap-1 rounded-lg px-2 py-1 text-xs font-medium text-violet-500 hover:bg-violet-500/10" disabled={add.isPending} onClick={() => input.current?.click()}>
            <Paperclip size={13} /> {add.isPending ? 'Caricamento…' : 'Allega file'}
          </button>
          <input ref={input} type="file" accept={ACCEPT} hidden onChange={e => { const f = e.target.files?.[0]; if (f) add.mutate(f); e.target.value = '' }} />
        </>
      )}
    </div>
  )
}
