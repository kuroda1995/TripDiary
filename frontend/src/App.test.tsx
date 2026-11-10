import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { server } from '@/test/msw/server'
import { renderApp } from '@/test/render'

describe('アプリ全体', () => {
  it('トップページを表示し、API が正常なら「API: 正常」と表示する', async () => {
    server.use(http.get('/actuator/health', () => HttpResponse.json({ status: 'UP' })))

    renderApp('/')

    expect(screen.getByRole('link', { name: 'TripDiary' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '旅の思い出を、みんなと。' })).toBeInTheDocument()
    expect(await screen.findByText('API: 正常')).toBeInTheDocument()
  })

  it('API に接続できない場合は「API: 接続できません」と表示する', async () => {
    server.use(http.get('/actuator/health', () => HttpResponse.json({ status: 'DOWN' }, { status: 503 })))

    renderApp('/')

    expect(await screen.findByText('API: 接続できません')).toBeInTheDocument()
  })

  it('存在しない URL では 404 ページを表示し、トップへ戻れる', async () => {
    server.use(http.get('/actuator/health', () => HttpResponse.json({ status: 'UP' })))

    renderApp('/no-such-page')

    expect(screen.getByRole('heading', { name: 'ページが見つかりません' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'トップへ戻る' })).toHaveAttribute('href', '/')
  })
})
