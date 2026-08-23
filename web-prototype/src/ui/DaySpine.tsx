/**
 * Day Spine — the signature element.
 *
 * One day, drawn to scale. Blocks are as tall as the event is long, free time is
 * left visibly empty, and "now" is a rule across the scale. Geometry comes from
 * domain/daySpine.ts; this file only draws it, so both themes render identical
 * numbers with different weight, radius and lane spacing.
 */

import { useMemo } from 'react'
import { buildDaySpine, scheduledMinutes } from '../domain/daySpine'
import { formatDuration, formatTime, timeOf } from '../domain/datetime'
import type { Event, EventStatus, IsoDate, IsoDateTime } from '../domain/types'
import { isTerminal, statusLabel } from './Chips'

/** Enough vertical room for a two-line label before the next one is pushed down. */
const MIN_LABEL_GAP_PCT = 11

interface DaySpineProps {
  date: IsoDate
  events: readonly Event[]
  now: IsoDateTime
  onSelect?: (event: Event) => void
}

export function DaySpine({ date, events, now, onSelect }: DaySpineProps) {
  const spine = useMemo(() => buildDaySpine(date, events, now), [date, events, now])

  // Labels are laid out separately from blocks: a 20-minute stop still needs a
  // readable title, so the label may sit lower than the block it names.
  const labels = useMemo(() => {
    let floor = -Infinity
    return [...spine.blocks]
      .sort((a, b) => a.startPct - b.startPct)
      .map((block) => {
        const pct = Math.max(block.startPct, floor + MIN_LABEL_GAP_PCT)
        floor = pct
        return { block, pct }
      })
  }, [spine.blocks])

  const presentStatuses = useMemo(() => {
    const seen: EventStatus[] = []
    for (const b of spine.blocks) if (!seen.includes(b.event.status)) seen.push(b.event.status)
    return seen
  }, [spine.blocks])

  const committed = scheduledMinutes(spine)

  return (
    <div>
      <div className="spine">
        <div className="spine__scale" aria-hidden="true">
          {spine.ticks.map((tick) =>
            tick.major ? (
              <span
                key={tick.hour}
                className="spine__tick spine__tick--major"
                style={{ top: `${tick.pct}%` }}
              >
                {String(tick.hour).padStart(2, '0')}
              </span>
            ) : (
              <span key={tick.hour} className="spine__tick-dash" style={{ top: `${tick.pct}%` }} />
            ),
          )}
        </div>

        <div className="spine__track">
          <div className="spine__rail" />

          {spine.gaps.map((gap) => (
            <div
              key={`gap-${gap.fromTime}`}
              className="spine__gap"
              style={{ top: `${gap.startPct}%`, height: `${gap.sizePct}%` }}
            />
          ))}

          {spine.blocks.map((block) => (
            <button
              key={block.event.id}
              type="button"
              className="spine__block"
              data-status={block.event.status}
              data-lane={block.lane}
              style={{ top: `${block.startPct}%`, height: `${block.sizePct}%` }}
              onClick={() => onSelect?.(block.event)}
              aria-label={`${block.event.title}, ${formatTime(block.event.startTime)} to ${formatTime(
                block.event.endTime,
              )}, ${statusLabel(block.event.status)}`}
            />
          ))}
        </div>

        <div className="spine__labels">
          {labels.map(({ block, pct }) => (
            <button
              key={block.event.id}
              type="button"
              className="spine__label"
              style={{ top: `${pct}%` }}
              onClick={() => onSelect?.(block.event)}
            >
              <span
                className={`spine__label-title${isTerminal(block.event.status) ? ' is-struck' : ''}`}
              >
                {block.event.title}
              </span>
              <span className="spine__label-meta">
                {timeOf(block.event.startTime)} · {formatDuration(block.durationMinutes)}
              </span>
            </button>
          ))}

          {spine.gaps.map((gap) => (
            <span
              key={`gaplabel-${gap.fromTime}`}
              className="spine__gap-label"
              style={{ top: `${gap.startPct + gap.sizePct / 2}%` }}
            >
              {formatDuration(gap.minutes)} free
            </span>
          ))}
        </div>

        {spine.nowPct !== null && (
          <div className="spine__now" style={{ top: `${spine.nowPct}%` }}>
            <span className="spine__now-dot" />
            <span className="spine__now-time">{timeOf(now)}</span>
          </div>
        )}
      </div>

      <div className="spine__legend">
        <span className="spine__legend-item">
          {spine.blocks.length} {spine.blocks.length === 1 ? 'stop' : 'stops'}
        </span>
        <span className="spine__legend-item">{formatDuration(committed)} committed</span>
        {presentStatuses.map((status) => (
          <span key={status} className="spine__legend-item">
            {statusLabel(status)}
          </span>
        ))}
      </div>
    </div>
  )
}
