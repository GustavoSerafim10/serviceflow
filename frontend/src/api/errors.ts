/** Erro HTTP da API. A API responde erros como ProblemDetail (RFC 7807): title + detail. */
export class ApiError extends Error {
  readonly status: number
  readonly title: string

  constructor(status: number, title: string, detail: string) {
    super(detail || title)
    this.name = 'ApiError'
    this.status = status
    this.title = title
  }
}
