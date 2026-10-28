import { fireEvent, render, screen } from '@testing-library/react'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'
import BottomSheet from './BottomSheet.jsx'
import ConfirmModal from './ConfirmModal.jsx'
import { Chip, ChipRow } from './Chip.jsx'
import Fab from './Fab.jsx'
import PageHeader from './PageHeader.jsx'
import Tabs from './Tabs.jsx'

describe('PageHeader', () => {
  it('renders the title, the actions and a back button only when asked', () => {
    const onBack = vi.fn()
    const { rerender } = render(<PageHeader title="Quedadas" actions={<button>Buscar</button>} />)
    expect(screen.getByRole('heading', { name: 'Quedadas' })).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Buscar' })).toBeTruthy()
    expect(screen.queryByRole('button', { name: 'Volver atrás' })).toBeNull()

    rerender(<PageHeader title="Quedadas" onBack={onBack} />)
    fireEvent.click(screen.getByRole('button', { name: 'Volver atrás' }))
    expect(onBack).toHaveBeenCalledTimes(1)
  })

  it('takes a custom back label and can disable it while saving', () => {
    const onBack = vi.fn()
    const { rerender } = render(<PageHeader title="Crear grupo" onBack={onBack} backLabel="Volver a contactos" />)
    fireEvent.click(screen.getByRole('button', { name: 'Volver a contactos' }))
    expect(onBack).toHaveBeenCalledTimes(1)

    rerender(<PageHeader title="Crear grupo" onBack={onBack} backLabel="Volver a contactos" backDisabled />)
    expect(screen.getByRole('button', { name: 'Volver a contactos' })).toBeDisabled()
  })

  it('renders custom content instead of the title heading', () => {
    render(<PageHeader><span>Lucía · En línea</span></PageHeader>)
    expect(screen.getByText('Lucía · En línea')).toBeTruthy()
    expect(screen.queryByRole('heading')).toBeNull()
  })
})

describe('Tabs', () => {
  const tabs = [
    { id: 'all', label: 'Todo' },
    { id: 'people', label: 'Personas' },
    { id: 'events', label: 'Quedadas' },
  ]

  function Harness() {
    const [value, setValue] = useState('all')
    return <Tabs tabs={tabs} value={value} onChange={setValue} label="Resultados" />
  }

  it('marks the active tab and changes on click', () => {
    render(<Harness />)
    expect(screen.getByRole('tab', { name: 'Todo' })).toHaveAttribute('aria-selected', 'true')
    fireEvent.click(screen.getByRole('tab', { name: 'Personas' }))
    expect(screen.getByRole('tab', { name: 'Personas' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('tab', { name: 'Todo' })).toHaveAttribute('aria-selected', 'false')
  })

  it('moves with the arrow keys, wrapping around', () => {
    render(<Harness />)
    const list = screen.getByRole('tablist', { name: 'Resultados' })
    fireEvent.keyDown(list, { key: 'ArrowRight' })
    expect(screen.getByRole('tab', { name: 'Personas' })).toHaveAttribute('aria-selected', 'true')
    fireEvent.keyDown(list, { key: 'ArrowLeft' })
    fireEvent.keyDown(list, { key: 'ArrowLeft' })
    expect(screen.getByRole('tab', { name: 'Quedadas' })).toHaveAttribute('aria-selected', 'true')
  })
})

describe('Chip', () => {
  it('toggles aria-pressed and calls onClick', () => {
    const onClick = vi.fn()
    render(
      <ChipRow label="Filtros">
        <Chip selected onClick={onClick}>Hoy</Chip>
        <Chip>Este finde</Chip>
      </ChipRow>,
    )
    expect(screen.getByRole('group', { name: 'Filtros' })).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Hoy' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: 'Este finde' })).toHaveAttribute('aria-pressed', 'false')
    fireEvent.click(screen.getByRole('button', { name: 'Hoy' }))
    expect(onClick).toHaveBeenCalledTimes(1)
  })
})

describe('Fab', () => {
  it('is an icon button named by its label', () => {
    const onClick = vi.fn()
    render(<Fab icon={<span>+</span>} label="Crear quedada" onClick={onClick} />)
    fireEvent.click(screen.getByRole('button', { name: 'Crear quedada' }))
    expect(onClick).toHaveBeenCalledTimes(1)
  })
})

describe('BottomSheet', () => {
  it('renders nothing when closed', () => {
    render(<BottomSheet open={false} title="Filtros" onClose={() => {}}>x</BottomSheet>)
    expect(screen.queryByRole('dialog')).toBeNull()
  })

  it('is a labelled modal dialog, closes on Escape and returns focus', () => {
    const onClose = vi.fn()
    function Harness() {
      const [open, setOpen] = useState(false)
      return (
        <>
          <button onClick={() => setOpen(true)}>Abrir</button>
          <BottomSheet open={open} title="Filtros" onClose={() => { onClose(); setOpen(false) }}>
            <button>Aplicar</button>
          </BottomSheet>
        </>
      )
    }
    render(<Harness />)
    const opener = screen.getByRole('button', { name: 'Abrir' })
    opener.focus()
    fireEvent.click(opener)
    const dialog = screen.getByRole('dialog', { name: 'Filtros' })
    expect(dialog).toHaveAttribute('aria-modal', 'true')
    expect(screen.getByRole('button', { name: 'Aplicar' })).toHaveFocus()

    fireEvent.keyDown(document, { key: 'Escape' })
    expect(onClose).toHaveBeenCalledTimes(1)
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(opener).toHaveFocus()
  })

  it('traps focus with Tab', () => {
    render(
      <BottomSheet open title="Filtros" onClose={() => {}} draggable={false}>
        <button>Uno</button>
        <button>Dos</button>
      </BottomSheet>,
    )
    const [uno, dos] = [screen.getByRole('button', { name: 'Uno' }), screen.getByRole('button', { name: 'Dos' })]
    dos.focus()
    fireEvent.keyDown(document, { key: 'Tab' })
    expect(uno).toHaveFocus()
    fireEvent.keyDown(document, { key: 'Tab', shiftKey: true })
    expect(dos).toHaveFocus()
  })

  it('closes when the handle is dragged down far enough, not for a short drag', () => {
    const onClose = vi.fn()
    render(<BottomSheet open title="Filtros" onClose={onClose}>x</BottomSheet>)
    const handle = screen.getByTestId('bottom-sheet-handle')
    fireEvent.pointerDown(handle, { clientY: 100, pointerId: 1 })
    fireEvent.pointerMove(handle, { clientY: 140, pointerId: 1 })
    fireEvent.pointerUp(handle, { pointerId: 1 })
    expect(onClose).not.toHaveBeenCalled()

    fireEvent.pointerDown(handle, { clientY: 100, pointerId: 1 })
    fireEvent.pointerMove(handle, { clientY: 260, pointerId: 1 })
    fireEvent.pointerUp(handle, { pointerId: 1 })
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('has no handle when draggable is off', () => {
    render(<BottomSheet open title="Filtros" onClose={() => {}} draggable={false}>x</BottomSheet>)
    expect(screen.queryByTestId('bottom-sheet-handle')).toBeNull()
  })
})

describe('ConfirmModal', () => {
  it('confirms and cancels through its buttons', () => {
    const onConfirm = vi.fn()
    const onCancel = vi.fn()
    render(
      <ConfirmModal open title="Bloquear" message="¿Seguro?" confirmLabel="Bloquear" destructive
        onConfirm={onConfirm} onCancel={onCancel} />,
    )
    expect(screen.getByText('¿Seguro?')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Bloquear' }))
    expect(onConfirm).toHaveBeenCalledTimes(1)
    fireEvent.click(screen.getByRole('button', { name: 'Cancelar' }))
    expect(onCancel).toHaveBeenCalledTimes(1)
  })
})
