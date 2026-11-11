// CloudFront Functions（viewer request）: SPA のルーティング
//
// 拡張子のないパス（/spots/1 など React Router の画面）を /index.html に書き換える。
// 403/404 のカスタムエラーレスポンスで index.html を返す方法は、/api/* のエラー（404 の JSON など）まで
// 書き換えてしまうため使わない。この関数は既定の動作（S3）にだけ関連付ける。
function handler(event) {
  var request = event.request;
  var uri = request.uri;
  var lastSegment = uri.substring(uri.lastIndexOf('/') + 1);

  if (lastSegment.indexOf('.') === -1) {
    request.uri = '/index.html';
  }
  return request;
}
