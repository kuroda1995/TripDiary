import { setupServer } from 'msw/node'
import { defaultHandlers } from './handlers'

/** テスト用のモックサーバー。各テストで server.use(...) によりハンドラを上書き・追加する。 */
export const server = setupServer(...defaultHandlers)
