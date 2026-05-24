# Landing Page Component Examples

This reference provides complete, production-ready component implementations using ShadCN UI.

## Hero Section (Elements 1-5)

```typescript
// components/Hero.tsx
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import Image from 'next/image'
import { ArrowRight, Play } from 'lucide-react'

export default function Hero() {
  return (
    <section className="relative min-h-screen flex items-center justify-center bg-gradient-to-b from-blue-50 to-white overflow-hidden">
      {/* Background decorations */}
      <div className="absolute inset-0 -z-10">
        <div className="absolute top-20 left-10 w-72 h-72 bg-blue-200 rounded-full mix-blend-multiply filter blur-xl opacity-70 animate-blob" />
        <div className="absolute top-40 right-10 w-72 h-72 bg-purple-200 rounded-full mix-blend-multiply filter blur-xl opacity-70 animate-blob animation-delay-2000" />
      </div>

      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-20">
        <div className="grid lg:grid-cols-2 gap-12 items-center">
          {/* Left Content */}
          <div className="space-y-8">
            {/* Badge for announcement */}
            <Badge variant="secondary" className="w-fit">
              New feature: AI automation update
            </Badge>

            {/* Element 3: SEO Optimized Title */}
            <h1 className="text-4xl sm:text-5xl lg:text-6xl font-bold text-gray-900 leading-tight">
              Make team collaboration{' '}
              <span className="text-blue-600 bg-gradient-to-r from-blue-600 to-purple-600 bg-clip-text text-transparent">
                10x faster
              </span>
              <br />
              with our project management tool
            </h1>

            {/* Subtitle */}
            <p className="text-lg sm:text-xl text-gray-600 leading-relaxed max-w-2xl">
              Even complex projects become simple. A workspace that connects every member of your team.
            </p>

            {/* Element 4: Primary CTA with ShadCN Button */}
            <div className="flex flex-col sm:flex-row gap-4">
              <Button size="lg" className="text-lg px-8 py-6 shadow-lg hover:shadow-xl transition-all">
                Start free
                <ArrowRight className="ml-2 h-5 w-5" />
              </Button>
              <Button size="lg" variant="outline" className="text-lg px-8 py-6">
                <Play className="mr-2 h-5 w-5" />
                Watch demo
              </Button>
            </div>

            {/* Element 5: Social Proof */}
            <div className="flex flex-col sm:flex-row items-start sm:items-center gap-6 pt-4">
              <div className="flex items-center gap-2">
                <div className="flex">
                  {[...Array(5)].map((_, i) => (
                    <svg
                      key={i}
                      className="w-5 h-5 text-yellow-400 fill-current"
                      viewBox="0 0 20 20"
                    >
                      <path d="M10 15l-5.878 3.09 1.123-6.545L.489 6.91l6.572-.955L10 0l2.939 5.955 6.572.955-4.756 4.635 1.123 6.545z" />
                    </svg>
                  ))}
                </div>
                <span className="text-sm font-medium text-gray-600">5.0 (2,341 reviews)</span>
              </div>
              <div className="h-4 w-px bg-gray-300 hidden sm:block" />
              <div className="text-gray-600">
                <span className="font-bold text-gray-900">5,000+</span> teams trust us
              </div>
            </div>

            {/* Trusted by logos */}
            <div className="pt-8 border-t">
              <p className="text-sm text-gray-500 mb-4">Trusted by</p>
              <div className="flex flex-wrap gap-8 items-center opacity-60 grayscale hover:grayscale-0 transition-all">
                {/* Company logos */}
              </div>
            </div>
          </div>

          {/* Right Content - Element 6: Image/Video */}
          <div className="relative">
            <div className="relative rounded-2xl overflow-hidden shadow-2xl ring-1 ring-gray-900/10">
              <Image
                src="/images/dashboard-preview.jpg"
                alt="Project management dashboard preview"
                width={1200}
                height={800}
                priority
                className="w-full h-auto"
              />
              {/* Play button overlay */}
              <Button
                size="lg"
                className="absolute inset-0 m-auto w-fit h-fit rounded-full p-6"
                variant="secondary"
              >
                <Play className="h-8 w-8 fill-current" />
              </Button>
            </div>
          </div>
        </div>
      </div>
    </section>
  )
}
```

## Benefits Section (Element 7)

```typescript
// components/Benefits.tsx
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/components/ui/card'
import { Clock, DollarSign, Zap, Shield, Users, Rocket } from 'lucide-react'

const benefits = [
  { icon: Clock, title: 'Save time', description: 'Automate complex work and save 2 hours every day' },
  { icon: DollarSign, title: 'Cut cost', description: 'Reduce operating cost by ~30% per month' },
  { icon: Zap, title: 'Easy to use', description: 'Intuitive interface, get started in 5 minutes' },
  { icon: Shield, title: 'Secure', description: 'Enterprise-grade security keeps your data safe' },
  { icon: Users, title: 'Team collaboration', description: 'Real-time collaboration to maximize productivity' },
  { icon: Rocket, title: 'Scale fast', description: 'Scalable infrastructure that grows with your business' },
]

export default function Benefits() {
  return (
    <section className="py-20 bg-gray-50">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="text-center mb-16">
          <h2 className="text-3xl sm:text-4xl font-bold text-gray-900 mb-4">
            Why pick our product?
          </h2>
          <p className="text-lg text-gray-600 max-w-2xl mx-auto">
            Best-in-class features and service supporting your success.
          </p>
        </div>

        <div className="grid md:grid-cols-2 lg:grid-cols-3 gap-6">
          {benefits.map((benefit, index) => (
            <Card key={index} className="hover:shadow-lg transition-all duration-300 border-none">
              <CardHeader>
                <div className="w-12 h-12 rounded-lg bg-blue-50 flex items-center justify-center mb-4">
                  <benefit.icon className="w-6 h-6 text-blue-600" />
                </div>
                <CardTitle className="text-xl">{benefit.title}</CardTitle>
              </CardHeader>
              <CardContent>
                <CardDescription className="text-base">
                  {benefit.description}
                </CardDescription>
              </CardContent>
            </Card>
          ))}
        </div>
      </div>
    </section>
  )
}
```

## Testimonials Section (Element 8)

```typescript
// components/Testimonials.tsx
import { Card, CardContent } from '@/components/ui/card'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { Badge } from '@/components/ui/badge'
import { Quote } from 'lucide-react'

const testimonials = [
  { name: 'Alice', role: 'CEO', company: 'TechCo', image: '/p1.jpg', rating: 5, content: 'Productivity tripled after switching to this tool.' },
  { name: 'Bob', role: 'PM', company: 'GlobalIT', image: '/p2.jpg', rating: 5, content: 'Team collaboration has never been this easy.' },
  { name: 'Carol', role: 'CTO', company: 'Fintech', image: '/p3.jpg', rating: 5, content: 'Fast support and rock-solid uptime.' },
]

export default function Testimonials() {
  return (
    <section className="py-20 bg-white">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="text-center mb-16">
          <Badge variant="secondary" className="mb-4">Customer reviews</Badge>
          <h2 className="text-3xl sm:text-4xl font-bold text-gray-900 mb-4">What our users say</h2>
          <p className="text-lg text-gray-600 max-w-2xl mx-auto">
            More than 5,000 teams already use our product.
          </p>
        </div>

        <div className="grid md:grid-cols-2 lg:grid-cols-3 gap-6">
          {testimonials.map((t, i) => (
            <Card key={i} className="hover:shadow-lg transition-all duration-300">
              <CardContent className="pt-6">
                <Quote className="w-8 h-8 text-blue-600 mb-4 opacity-50" />
                <div className="flex mb-4">
                  {[...Array(t.rating)].map((_, k) => (
                    <svg key={k} className="w-5 h-5 text-yellow-400 fill-current" viewBox="0 0 20 20">
                      <path d="M10 15l-5.878 3.09 1.123-6.545L.489 6.91l6.572-.955L10 0l2.939 5.955 6.572.955-4.756 4.635 1.123 6.545z" />
                    </svg>
                  ))}
                </div>
                <p className="text-gray-700 mb-6 leading-relaxed">"{t.content}"</p>
                <div className="flex items-center gap-3 pt-4 border-t">
                  <Avatar>
                    <AvatarImage src={t.image} alt={t.name} />
                    <AvatarFallback>{t.name.charAt(0)}</AvatarFallback>
                  </Avatar>
                  <div>
                    <p className="font-semibold text-gray-900">{t.name}</p>
                    <p className="text-sm text-gray-600">{t.role}, {t.company}</p>
                  </div>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      </div>
    </section>
  )
}
```

## FAQ Section (Element 9)

```typescript
// components/FAQ.tsx
'use client'
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from '@/components/ui/accordion'
import { Badge } from '@/components/ui/badge'

const faqs = [
  { question: 'How long is the free trial?', answer: '14 days, full feature access, no credit card required.' },
  { question: 'Can I use it month to month?', answer: 'Yes, monthly subscription with no long-term contract.' },
  { question: 'How is this different from competitors?', answer: 'Intuitive UI, strong automation, 24/7 support.' },
]

export default function FAQ() {
  return (
    <section className="py-20 bg-gray-50">
      <div className="max-w-3xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="text-center mb-12">
          <Badge variant="secondary" className="mb-4">FAQ</Badge>
          <h2 className="text-3xl sm:text-4xl font-bold text-gray-900 mb-4">Frequently asked questions</h2>
          <p className="text-lg text-gray-600">Reach out anytime if you have more questions.</p>
        </div>

        <Accordion type="single" collapsible className="w-full space-y-4">
          {faqs.map((faq, index) => (
            <AccordionItem key={index} value={`item-${index}`} className="bg-white rounded-lg border px-6">
              <AccordionTrigger className="text-left hover:no-underline">
                <span className="font-semibold text-lg pr-4">{faq.question}</span>
              </AccordionTrigger>
              <AccordionContent className="text-gray-600 text-base leading-relaxed">
                {faq.answer}
              </AccordionContent>
            </AccordionItem>
          ))}
        </Accordion>
      </div>
    </section>
  )
}
```

## Final CTA Section (Element 10)

```typescript
// components/FinalCTA.tsx
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { ArrowRight, CheckCircle } from 'lucide-react'

export default function FinalCTA() {
  return (
    <section className="py-20 bg-gradient-to-br from-blue-600 to-purple-700 relative overflow-hidden">
      <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10">
        <Card className="p-8 sm:p-12 text-center border-none shadow-2xl">
          <h2 className="text-3xl sm:text-4xl lg:text-5xl font-bold text-gray-900 mb-6">
            Get started now
          </h2>
          <p className="text-lg sm:text-xl text-gray-600 mb-8 max-w-2xl mx-auto">
            Try every feature free for 14 days.
          </p>

          <div className="flex flex-col sm:flex-row justify-center items-center gap-4 sm:gap-8 mb-8 text-gray-700">
            <div className="flex items-center gap-2">
              <CheckCircle className="w-5 h-5 text-green-600" />
              <span>14-day free trial</span>
            </div>
            <div className="flex items-center gap-2">
              <CheckCircle className="w-5 h-5 text-green-600" />
              <span>No credit card needed</span>
            </div>
          </div>

          <div className="flex flex-col sm:flex-row gap-4 justify-center">
            <Button size="lg" className="text-lg px-8 py-6">
              Start free
              <ArrowRight className="ml-2 h-5 w-5" />
            </Button>
          </div>
        </Card>
      </div>
    </section>
  )
}
```

## Footer (Element 11)

```typescript
// components/Footer.tsx
import { Separator } from '@/components/ui/separator'
import Link from 'next/link'

export default function Footer() {
  return (
    <footer className="bg-gray-900 text-white">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
        <div className="grid grid-cols-2 md:grid-cols-4 gap-8 mb-8">
          <div className="col-span-2 md:col-span-1">
            <h3 className="font-bold text-lg mb-4">Company name</h3>
            <div className="space-y-2 text-sm text-gray-400">
              <p>123 Tech Avenue, Seoul</p>
              <p>support@example.com</p>
              <p>+82 02-1234-5678</p>
            </div>
          </div>

          <div>
            <h3 className="font-bold text-lg mb-4">Legal</h3>
            <ul className="space-y-2 text-sm text-gray-400">
              <li><Link href="/privacy" className="hover:text-white">Privacy policy</Link></li>
              <li><Link href="/terms" className="hover:text-white">Terms of service</Link></li>
            </ul>
          </div>
        </div>

        <Separator className="bg-gray-800 mb-8" />

        <div className="text-center">
          <p className="text-sm text-gray-400">
            (c) 2024 Company Name. All rights reserved.
          </p>
        </div>
      </div>
    </footer>
  )
}
```
