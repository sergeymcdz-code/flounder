import { test, expect } from '@playwright/test'

for (const width of [375, 767, 768, 1366]) {
  test(`UX-20 judge lane layout and action bounds at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 812 })
    await page.addInitScript(() => sessionStorage.setItem('flounder.auth-session', JSON.stringify({
      accessToken: `demo.${btoa(JSON.stringify({ roles: ['judge'] }))}.demo`,
      tokenType: 'Bearer', expiresAt: Date.now() + 3600000,
    })))
    await page.goto('/operator/competition-units/00000010-1111-4111-8111-000000000001?eventId=11111111-1111-4111-8111-111111111111')
    const inputs = page.getByRole('textbox')
    await expect(inputs.first()).toBeVisible()
    const actions = page.getByRole('group', { name: 'Действия заплыва' })
    if (width <= 767) {
      await expect(page.getByRole('table')).toHaveCount(0)
      await expect(page.getByRole('article')).toHaveCount(await inputs.count())
      await actions.locator('summary').click()
      for (const button of await actions.getByRole('button').all()) {
        const box = (await button.boundingBox())!
        expect(box.x).toBeGreaterThanOrEqual(0)
        expect(box.x + box.width).toBeLessThanOrEqual(width)
        expect(box.height).toBeGreaterThanOrEqual(44)
      }
      await actions.locator('summary').click()
      for (const target of [page.getByRole('link', { name: '← К соревнованию' }), page.locator('summary').filter({ hasText: 'Как вводить' })]) {
        expect((await target.boundingBox())!.height).toBeGreaterThanOrEqual(44)
      }
      await inputs.last().focus()
      const last = (await inputs.last().boundingBox())!
      expect(last.y + last.height).toBeLessThanOrEqual((await actions.boundingBox())!.y)
      await inputs.first().fill('003215')
      await inputs.first().press('ArrowDown')
      await expect(inputs.nth(1)).toBeFocused()
      await inputs.nth(1).press('ArrowUp')
      await expect(inputs.first()).toBeFocused()
      await inputs.first().press('Enter')
      await expect(inputs.nth(1)).toBeFocused()
      await page.setViewportSize({ width: 1366, height: 812 })
      await expect(page.getByRole('table')).toBeVisible()
      await expect(inputs.first()).toHaveValue('00:32.15')
    } else {
      await expect(page.getByRole('table')).toBeVisible()
      await expect(page.getByRole('article')).toHaveCount(0)
      if (width === 768) await expect(page.locator('[data-scroll-edges]')).toHaveAttribute('data-scroll-edges', /right/)
    }
  })
}
