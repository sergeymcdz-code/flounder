import { Component, type ReactNode } from 'react'

export class RouteErrorBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false }

  static getDerivedStateFromError() {
    return { failed: true }
  }

  render() {
    if (this.state.failed) {
      return (
        <section className="fl-card" role="alert">
          <h2>Не удалось открыть раздел</h2>
          <p>Попробуйте обновить раздел или выберите другую вкладку.</p>
          <button className="fl-btn fl-btn-secondary" type="button" onClick={() => this.setState({ failed: false })}>
            Обновить
          </button>
        </section>
      )
    }
    return this.props.children
  }
}
