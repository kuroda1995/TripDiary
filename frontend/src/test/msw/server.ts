import { setupServer } from 'msw/node'

/** テスト用のモックサーバー。各テストで server.use(...) によりハンドラを追加する。 */
export const server = setupServer()
