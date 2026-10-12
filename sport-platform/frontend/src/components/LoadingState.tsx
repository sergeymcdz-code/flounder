type LoadingStateProps = {
    message?: string
}

export function LoadingState({
    message = 'Загрузка данных…',
    }: LoadingStateProps) {
    return (
        <div className="state-card" role="status" aria-live="polite">
        {message}
        </div>
    )
}