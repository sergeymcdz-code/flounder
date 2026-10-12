type ErrorStateProps = {
    title?: string
    message: string
    onRetry?: () => void
}

export function ErrorState({
    title = 'Не удалось загрузить данные',
    message,
    onRetry,
    }: ErrorStateProps) {
    return (
        <div className="state-card state-card-error" role="alert">
        <h2>{title}</h2>
        <p>{message}</p>

        {onRetry ? (
            <button className="button button-secondary" type="button" onClick={onRetry}>
            Повторить
            </button>
        ) : null}
        </div>
    )
}