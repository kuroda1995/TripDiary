// CloudFront Functions（viewer request）: 待機モードの画面（docs/deploy.md 4章）
//
// API（ALB・ECS）を止めている間に使う。どのページを開いてもお知らせページ（/standby.html）を返し、
// 画面が読み込めずに壊れたように見えるのを防ぐ。API へのリクエストには 503 を返す（API への経路は外してある）。
function handler(event) {
  var request = event.request;
  var uri = request.uri;

  if (uri.indexOf('/api/') === 0 || uri === '/actuator/health') {
    return {
      statusCode: 503,
      statusDescription: 'Service Unavailable',
      headers: {
        'content-type': { value: 'application/json; charset=utf-8' },
        'cache-control': { value: 'no-store' },
      },
      body: {
        encoding: 'text',
        // 関数のコードは ASCII にしておく（日本語の文言はお知らせページに書く）
        data: '{"code":"STANDBY","message":"The demo environment is on standby."}',
      },
    };
  }

  if (uri !== '/favicon.svg') {
    request.uri = '/standby.html';
  }
  return request;
}
